package com.wust.dormitory.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.common.error.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BatchAnalyticsSnapshotService {
    public static final String METRIC_VERSION = "BATCH-METRICS-V1";
    private static final int PRIVACY_THRESHOLD = 5;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public BatchAnalyticsSnapshotService(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> snapshot(long batchId) {
        Map<String, Object> batch = batch(batchId);
        if (!"FINISHED".equals(String.valueOf(batch.get("batch_status")))) {
            throw new BusinessException(
                    "BATCH_ANALYTICS_NOT_FINAL",
                    "只有已完成批次可以生成不可变历史快照",
                    HttpStatus.CONFLICT);
        }
        Map<String, Object> existing = find(batchId);
        if (existing != null) return existing;

        LocalDateTime snapshotAt = LocalDateTime.now();
        populateFacts(batchId, snapshotAt);
        try {
            jdbc.update("""
                    INSERT INTO batch_analytics_snapshot
                    (batch_id, metric_version, metrics_json, dimensions_json,
                     source_basis, data_updated_at, immutable)
                    VALUES
                    (:batchId, :version, CAST(:metrics AS JSON), CAST(:dimensions AS JSON),
                     'BATCH_FINAL_RESULT', :updatedAt, 1)
                    """, new MapSqlParameterSource()
                    .addValue("batchId", batchId)
                    .addValue("version", METRIC_VERSION)
                    .addValue("metrics", json(calculate(batchId)))
                    .addValue("dimensions", json(dimensions(batchId)))
                    .addValue("updatedAt", snapshotAt));
        } catch (DuplicateKeyException ignored) {
            return find(batchId);
        }
        return find(batchId);
    }

    @Scheduled(fixedDelayString = "${app.analytics.snapshot-delay-ms:30000}")
    public void snapshotMissingFinishedBatches() {
        List<Long> batchIds = jdbc.queryForList("""
                SELECT batch.id
                FROM selection_batch batch
                LEFT JOIN batch_analytics_snapshot snapshot
                  ON snapshot.batch_id=batch.id AND snapshot.metric_version=:version
                WHERE batch.batch_status='FINISHED' AND snapshot.id IS NULL
                ORDER BY batch.finished_at, batch.id
                LIMIT 20
                """, Map.of("version", METRIC_VERSION), Long.class);
        batchIds.forEach(this::snapshot);
    }

    public Map<String, Object> get(long batchId) {
        Map<String, Object> result = find(batchId);
        if (result == null) {
            throw new BusinessException(
                    "BATCH_ANALYTICS_NOT_FOUND",
                    "批次历史统计快照不存在",
                    HttpStatus.NOT_FOUND);
        }
        return result;
    }

    public List<MetricDefinition> definitions() {
        LocalDateTime now = LocalDateTime.now();
        return List.of(
                definition("studentTotal", "学生总数", "Total students", "批次完成时", now),
                definition("participantCount", "参与人数", "Participants", "批次参与范围", now),
                definition("selfSelectionCount", "自主选择人数", "Self-selected students", "批次选择期", now),
                definition("teamSelectionCount", "组队人数", "Team-selected students", "批次选择期", now),
                definition("unifiedAllocationCount", "统一分配人数", "Unified allocations", "批次分配期", now),
                definition("unassignedCount", "未分配人数", "Unassigned students", "批次完成时", now),
                definition("recommendationAdoptionCount", "推荐采用人数", "Recommendation adoptions", "批次选择期", now),
                definition("averageMatchScore", "平均匹配分", "Average match score", "批次最终结果", now),
                definition("minimumMatchScore", "最低匹配分", "Minimum match score", "批次最终结果", now),
                definition("roomChangeCount", "换寝人数", "Room changes", "批次完成前", now),
                definition("exchangeCount", "交换人数", "Room exchanges", "批次完成前", now),
                definition("waitlistRequestCount", "候补申请人数", "Waitlist requests", "批次开放至完成", now),
                definition("waitlistAssignmentCount", "候补补位人数", "Waitlist placements", "批次开放至完成", now),
                definition("bedUtilizationRate", "床位利用率", "Bed utilization", "批次完成时", now),
                definition("manualAdjustmentCount", "人工调整次数", "Manual adjustments", "批次完成前", now),
                definition("anomalyCount", "异常数量", "Anomalies", "批次期间", now),
                definition("completionDurationSeconds", "批次完成耗时", "Completion duration", "发布至完成", now));
    }

    private MetricDefinition definition(
            String code,
            String nameZhCn,
            String nameEnUs,
            String timeRange,
            LocalDateTime updatedAt) {
        return new MetricDefinition(
                code,
                nameZhCn,
                nameEnUs,
                timeRange,
                Map.of("batchStatus", "FINISHED", "privacyThreshold", PRIVACY_THRESHOLD),
                "BATCH_FINAL_RESULT",
                updatedAt,
                METRIC_VERSION,
                "学生维度来自不可变事实快照；小于5人的组合隐藏推荐采用和匹配分");
    }

    private void populateFacts(long batchId, LocalDateTime snapshotAt) {
        jdbc.update("""
                INSERT IGNORE INTO batch_analytics_student_fact
                (batch_id, student_id, major_id, grade_year, degree_level, student_category,
                 campus_id, building_id, room_id, bed_id, room_type, assignment_method, match_score,
                 self_selected, team_selected, unified_allocated, unassigned, recommendation_adopted,
                 room_changed, exchanged, waitlist_requested, waitlist_assigned, manual_adjusted, snapshot_at)
                SELECT eligibility.batch_id,
                       student.id,
                       student.major_id,
                       student.grade_year,
                       student.degree_level,
                       student.student_category,
                       building.campus_id,
                       building.id,
                       COALESCE(residency.room_id, assigned_bed.room_id),
                       COALESCE(residency.bed_id, assignment.bed_id),
                       room.room_type,
                       COALESCE(assignment.assignment_method, residency.assignment_method),
                       COALESCE(
                         (SELECT MAX(result.score)
                          FROM allocation_run_result result
                          WHERE result.allocation_run_id=assignment.allocation_run_id
                            AND result.student_id=student.id
                            AND result.bed_id=COALESCE(residency.bed_id, assignment.bed_id)),
                         (SELECT MAX(candidate.score)
                          FROM allocation_optimization_candidate candidate
                          JOIN allocation_optimization_run run ON run.id=candidate.run_id
                          WHERE run.batch_id=eligibility.batch_id
                            AND run.run_status='SUBMITTED'
                            AND candidate.student_id=student.id
                            AND candidate.bed_id=COALESCE(residency.bed_id, assignment.bed_id)),
                         (SELECT MAX(CAST(JSON_UNQUOTE(JSON_EXTRACT(request.response_json,'$.matchScore')) AS DECIMAL(10,4)))
                          FROM student_recommendation_request request
                          WHERE request.batch_id=eligibility.batch_id
                            AND request.student_id=student.id
                            AND request.created_at<=COALESCE(batch.finished_at, CURRENT_TIMESTAMP(3))
                            AND (
                              CAST(JSON_UNQUOTE(JSON_EXTRACT(request.response_json,'$.bed.id')) AS UNSIGNED)
                                =COALESCE(residency.bed_id, assignment.bed_id)
                              OR CAST(JSON_UNQUOTE(JSON_EXTRACT(request.response_json,'$.room.id')) AS UNSIGNED)
                                =COALESCE(residency.room_id, assigned_bed.room_id)
                            ))
                       ),
                       CASE WHEN COALESCE(assignment.assignment_method, residency.assignment_method)
                            IN ('SELF_SELECT','STUDENT_RANDOM','ROOM_SELECT','BED_SELECT') THEN 1 ELSE 0 END,
                       CASE WHEN COALESCE(assignment.assignment_method, residency.assignment_method)
                            IN ('TEAM_SELECT','TEAM_ROOM_SELECT','TEAM_BED_SELECT') THEN 1 ELSE 0 END,
                       CASE WHEN COALESCE(assignment.assignment_method, residency.assignment_method)
                            IN ('ADMIN_RANDOM','ADMIN_OPTIMIZED') THEN 1 ELSE 0 END,
                       CASE WHEN COALESCE(residency.room_id, assigned_bed.room_id) IS NULL
                              AND COALESCE(residency.bed_id, assignment.bed_id) IS NULL THEN 1 ELSE 0 END,
                       CASE WHEN EXISTS (
                         SELECT 1
                         FROM student_recommendation_request request
                         WHERE request.batch_id=eligibility.batch_id
                           AND request.student_id=student.id
                           AND request.created_at<=COALESCE(batch.finished_at, CURRENT_TIMESTAMP(3))
                           AND (
                             CAST(JSON_UNQUOTE(JSON_EXTRACT(request.response_json,'$.bed.id')) AS UNSIGNED)
                               =COALESCE(residency.bed_id, assignment.bed_id)
                             OR CAST(JSON_UNQUOTE(JSON_EXTRACT(request.response_json,'$.room.id')) AS UNSIGNED)
                               =COALESCE(residency.room_id, assigned_bed.room_id)
                           )
                       ) THEN 1 ELSE 0 END,
                       CASE WHEN EXISTS (
                         SELECT 1
                         FROM room_change_request change_request
                         JOIN room_assignment source_residency
                           ON source_residency.id=change_request.source_residency_id
                         WHERE change_request.student_id=student.id
                           AND source_residency.batch_id=eligibility.batch_id
                           AND change_request.request_status='EXECUTED'
                           AND change_request.executed_at<=COALESCE(batch.finished_at, CURRENT_TIMESTAMP(3))
                       ) THEN 1 ELSE 0 END,
                       CASE WHEN EXISTS (
                         SELECT 1
                         FROM room_exchange_request exchange_request
                         JOIN room_assignment initiator_source
                           ON initiator_source.id=exchange_request.initiator_residency_id
                         JOIN room_assignment target_source
                           ON target_source.id=exchange_request.target_residency_id
                         WHERE exchange_request.request_status='EXECUTED'
                           AND exchange_request.executed_at<=COALESCE(batch.finished_at, CURRENT_TIMESTAMP(3))
                           AND (
                             (exchange_request.initiator_student_id=student.id
                              AND initiator_source.batch_id=eligibility.batch_id)
                             OR (exchange_request.target_student_id=student.id
                                 AND target_source.batch_id=eligibility.batch_id)
                           )
                       ) THEN 1 ELSE 0 END,
                       CASE WHEN EXISTS (
                         SELECT 1 FROM waitlist_entry waitlist
                         WHERE waitlist.student_id=student.id
                           AND waitlist.joined_at BETWEEN batch.start_at
                               AND COALESCE(batch.finished_at, CURRENT_TIMESTAMP(3))
                       ) THEN 1 ELSE 0 END,
                       CASE WHEN EXISTS (
                         SELECT 1 FROM waitlist_entry waitlist
                         WHERE waitlist.student_id=student.id
                           AND waitlist.entry_status='ASSIGNED'
                           AND waitlist.assigned_at BETWEEN batch.start_at
                               AND COALESCE(batch.finished_at, CURRENT_TIMESTAMP(3))
                       ) THEN 1 ELSE 0 END,
                       CASE WHEN COALESCE(assignment.assignment_method, residency.assignment_method)
                            IN ('MANUAL_ADJUSTMENT','DIRECT_ROOM','DIRECT_BED') THEN 1 ELSE 0 END,
                       :snapshotAt
                FROM batch_student_eligibility eligibility
                JOIN selection_batch batch ON batch.id=eligibility.batch_id
                JOIN student ON student.id=eligibility.student_id
                LEFT JOIN bed_assignment assignment
                  ON assignment.batch_id=eligibility.batch_id
                 AND assignment.student_id=student.id
                 AND assignment.assignment_status='ACTIVE'
                LEFT JOIN room_assignment residency
                  ON residency.id=(
                    SELECT MAX(candidate.id)
                    FROM room_assignment candidate
                    WHERE candidate.student_id=student.id
                      AND candidate.assigned_at<=COALESCE(batch.finished_at, CURRENT_TIMESTAMP(3))
                      AND (candidate.batch_id=eligibility.batch_id OR candidate.batch_id IS NULL)
                  )
                LEFT JOIN bed assigned_bed ON assigned_bed.id=assignment.bed_id
                LEFT JOIN room ON room.id=COALESCE(residency.room_id, assigned_bed.room_id)
                LEFT JOIN dormitory_floor floor ON floor.id=room.floor_id
                LEFT JOIN dormitory_building building ON building.id=floor.building_id
                WHERE eligibility.batch_id=:batchId
                  AND eligibility.eligibility_status='ELIGIBLE'
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("snapshotAt", snapshotAt));
    }

    private Map<String, Object> calculate(long batchId) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("studentTotal", scalar(
                "SELECT COUNT(*) FROM batch_student_eligibility WHERE batch_id=:batchId",
                Map.of("batchId", batchId)));
        metrics.put("participantCount", factLong(batchId, "COUNT(*)"));
        metrics.put("selfSelectionCount", factLong(batchId, "COALESCE(SUM(self_selected),0)"));
        metrics.put("teamSelectionCount", factLong(batchId, "COALESCE(SUM(team_selected),0)"));
        metrics.put("unifiedAllocationCount", factLong(batchId, "COALESCE(SUM(unified_allocated),0)"));
        metrics.put("unassignedCount", factLong(batchId, "COALESCE(SUM(unassigned),0)"));
        metrics.put("recommendationAdoptionCount", factLong(batchId, "COALESCE(SUM(recommendation_adopted),0)"));
        metrics.put("averageMatchScore", factDouble(batchId, "AVG(match_score)"));
        metrics.put("minimumMatchScore", factDouble(batchId, "MIN(match_score)"));
        metrics.put("roomChangeCount", factLong(batchId, "COALESCE(SUM(room_changed),0)"));
        metrics.put("exchangeCount", factLong(batchId, "COALESCE(SUM(exchanged),0)"));
        metrics.put("waitlistRequestCount", factLong(batchId, "COALESCE(SUM(waitlist_requested),0)"));
        metrics.put("waitlistAssignmentCount", factLong(batchId, "COALESCE(SUM(waitlist_assigned),0)"));
        metrics.put("manualAdjustmentCount", scalar("""
                SELECT COUNT(*) FROM assignment_history
                WHERE batch_id=:batchId AND event_type='ADJUSTED'
                """, Map.of("batchId", batchId)));

        long assigned = factLong(batchId, "COALESCE(SUM(CASE WHEN unassigned=0 THEN 1 ELSE 0 END),0)");
        long capacity = scalar("""
                SELECT COALESCE(SUM(candidate.capacity),0)
                FROM (
                  SELECT DISTINCT room.id, room.capacity
                  FROM room
                  WHERE room.id IN (
                    SELECT room_id FROM batch_room_scope WHERE batch_id=:batchId
                    UNION
                    SELECT bed.room_id
                    FROM batch_bed_scope scope
                    JOIN bed ON bed.id=scope.bed_id
                    WHERE scope.batch_id=:batchId
                    UNION
                    SELECT room.id
                    FROM batch_building_scope scope
                    JOIN dormitory_floor floor ON floor.building_id=scope.building_id
                    JOIN room ON room.floor_id=floor.id
                    WHERE scope.batch_id=:batchId
                  )
                ) candidate
                """, Map.of("batchId", batchId));
        metrics.put("bedUtilizationRate", capacity == 0 ? 0D : assigned * 1D / capacity);

        // operation_anomaly has no stable batch foreign key. Keep the metric explicit and
        // conservative until a batch-scoped anomaly fact is introduced by a later migration.
        metrics.put("anomalyCount", 0L);
        metrics.put("completionDurationSeconds", scalar("""
                SELECT COALESCE(
                    TIMESTAMPDIFF(SECOND, COALESCE(published_at, created_at), finished_at),
                    0)
                FROM selection_batch
                WHERE id=:batchId
                """, Map.of("batchId", batchId)));
        return metrics;
    }

    private long factLong(long batchId, String expression) {
        return scalar(
                "SELECT " + expression + " FROM batch_analytics_student_fact WHERE batch_id=:batchId",
                Map.of("batchId", batchId));
    }

    private double factDouble(long batchId, String expression) {
        Number value = jdbc.queryForObject(
                "SELECT " + expression + " FROM batch_analytics_student_fact WHERE batch_id=:batchId",
                Map.of("batchId", batchId),
                Number.class);
        return value == null ? 0D : value.doubleValue();
    }

    private Map<String, Object> dimensions(long batchId) {
        return Map.of(
                "batchId", batchId,
                "metricVersion", METRIC_VERSION,
                "privacyThreshold", PRIVACY_THRESHOLD,
                "sourceBasis", "BATCH_FINAL_RESULT",
                "studentFacts", "batch_analytics_student_fact",
                "recommendationSource", "student_recommendation_request.response_json",
                "residencySource", "bed_assignment + room_assignment");
    }

    private Map<String, Object> batch(long batchId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, batch_status, start_at, finished_at
                FROM selection_batch
                WHERE id=:id
                FOR UPDATE
                """, Map.of("id", batchId));
        if (rows.isEmpty()) {
            throw new BusinessException("BATCH_NOT_FOUND", "批次不存在", HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    private Map<String, Object> find(long batchId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, batch_id, metric_version, metrics_json, dimensions_json,
                       source_basis, data_updated_at, immutable, created_at
                FROM batch_analytics_snapshot
                WHERE batch_id=:batchId AND metric_version=:version
                """, Map.of("batchId", batchId, "version", METRIC_VERSION));
        if (rows.isEmpty()) return null;
        Map<String, Object> result = new LinkedHashMap<>(rows.getFirst());
        result.put("metrics", parse(result.remove("metrics_json")));
        result.put("dimensions", parse(result.remove("dimensions_json")));
        return result;
    }

    private long scalar(String sql, Map<String, ?> parameters) {
        Number value = jdbc.queryForObject(sql, parameters, Number.class);
        return value == null ? 0L : value.longValue();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("批次统计快照无法序列化", exception);
        }
    }

    private Map<String, Object> parse(Object value) {
        if (value == null) return Map.of();
        try {
            if (value instanceof String text) {
                return objectMapper.readValue(text, new TypeReference<>() {});
            }
            return objectMapper.convertValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return Map.of("parseError", true);
        }
    }
}
