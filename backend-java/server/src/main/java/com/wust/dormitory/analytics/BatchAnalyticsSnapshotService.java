package com.wust.dormitory.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.common.error.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BatchAnalyticsSnapshotService {
    public static final String METRIC_VERSION = "BATCH-METRICS-V1";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public BatchAnalyticsSnapshotService(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper) {
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
        Map<String, Object> existing = find(batchId, METRIC_VERSION);
        if (existing != null) {
            return existing;
        }
        LocalDateTime dataUpdatedAt = LocalDateTime.now();
        Map<String, Object> metrics = calculate(batchId);
        Map<String, Object> dimensions = dimensions(batchId);
        try {
            jdbc.update("""
                    INSERT INTO batch_analytics_snapshot
                    (batch_id, metric_version, metrics_json, dimensions_json,
                     source_basis, data_updated_at, immutable)
                    VALUES
                    (:batchId,:metricVersion,CAST(:metrics AS JSON),
                     CAST(:dimensions AS JSON),'BATCH_FINAL_RESULT',:updatedAt,1)
                    """, new MapSqlParameterSource()
                    .addValue("batchId", batchId)
                    .addValue("metricVersion", METRIC_VERSION)
                    .addValue("metrics", json(metrics))
                    .addValue("dimensions", json(dimensions))
                    .addValue("updatedAt", dataUpdatedAt));
        } catch (DuplicateKeyException ignored) {
            return find(batchId, METRIC_VERSION);
        }
        return find(batchId, METRIC_VERSION);
    }

    public Map<String, Object> get(long batchId) {
        Map<String, Object> snapshot = find(batchId, METRIC_VERSION);
        if (snapshot != null) return snapshot;
        throw new BusinessException("BATCH_ANALYTICS_NOT_FOUND", "批次历史统计快照不存在", HttpStatus.NOT_FOUND);
    }

    public List<MetricDefinition> definitions() {
        LocalDateTime now = LocalDateTime.now();
        return List.of(
                definition("studentTotal", "学生总数", "Total students", "批次创建至完成", "BATCH_FINAL_RESULT", now),
                definition("participantCount", "参与人数", "Participants", "批次开放范围", "BATCH_FINAL_RESULT", now),
                definition("selfSelectionCount", "自主选择人数", "Self-selected students", "批次选择期", "BATCH_FINAL_RESULT", now),
                definition("teamSelectionCount", "组队人数", "Team-selected students", "批次选择期", "BATCH_FINAL_RESULT", now),
                definition("unifiedAllocationCount", "统一分配人数", "Unified allocations", "批次分配期", "BATCH_FINAL_RESULT", now),
                definition("unassignedCount", "未分配人数", "Unassigned students", "批次完成时", "BATCH_FINAL_RESULT", now),
                definition("recommendationAdoptionCount", "推荐采用人数", "Recommendation adoptions", "批次选择期", "BATCH_FINAL_RESULT", now),
                definition("averageMatchScore", "平均匹配分", "Average match score", "批次最终结果", "BATCH_FINAL_RESULT", now),
                definition("minimumMatchScore", "最低匹配分", "Minimum match score", "批次最终结果", "BATCH_FINAL_RESULT", now),
                definition("roomChangeCount", "换寝人数", "Room changes", "批次结束前", "BATCH_FINAL_RESULT", now),
                definition("exchangeCount", "交换人数", "Room exchanges", "批次结束前", "BATCH_FINAL_RESULT", now),
                definition("waitlistRequestCount", "候补申请人数", "Waitlist requests", "批次期间", "BATCH_FINAL_RESULT", now),
                definition("waitlistAssignmentCount", "候补补位人数", "Waitlist placements", "批次期间", "BATCH_FINAL_RESULT", now),
                definition("bedUtilizationRate", "床位利用率", "Bed utilization", "批次完成时", "BATCH_FINAL_RESULT", now),
                definition("manualAdjustmentCount", "人工调整次数", "Manual adjustments", "批次期间", "BATCH_FINAL_RESULT", now),
                definition("anomalyCount", "异常数量", "Anomalies", "批次期间", "BATCH_FINAL_RESULT", now),
                definition("completionDurationSeconds", "批次完成耗时", "Completion duration", "创建至完成", "BATCH_FINAL_RESULT", now));
    }

    private MetricDefinition definition(
            String code,
            String zh,
            String en,
            String timeRange,
            String sourceBasis,
            LocalDateTime updatedAt) {
        return new MetricDefinition(
                code,
                zh,
                en,
                timeRange,
                Map.of("batchStatus", "FINISHED"),
                sourceBasis,
                updatedAt,
                METRIC_VERSION,
                "小样本组合低于5人时不展示个人偏好维度");
    }

    private Map<String, Object> calculate(long batchId) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("studentTotal", scalar("SELECT COUNT(*) FROM student", Map.of()));
        metrics.put("participantCount", scalar("SELECT COUNT(*) FROM batch_student_eligibility WHERE batch_id=:batchId AND eligibility_status='ELIGIBLE'", Map.of("batchId", batchId)));
        metrics.put("selfSelectionCount", scalar("SELECT COUNT(*) FROM bed_assignment WHERE batch_id=:batchId AND assignment_source='SELF_SELECTION'", Map.of("batchId", batchId)));
        metrics.put("teamSelectionCount", scalar("SELECT COUNT(*) FROM bed_assignment WHERE batch_id=:batchId AND assignment_source='TEAM_SELECTION'", Map.of("batchId", batchId)));
        metrics.put("unifiedAllocationCount", scalar("SELECT COUNT(*) FROM bed_assignment WHERE batch_id=:batchId AND assignment_source='UNIFIED_ALLOCATION'", Map.of("batchId", batchId)));
        metrics.put("unassignedCount", scalar("""
                SELECT COUNT(*) FROM batch_student_eligibility e
                WHERE e.batch_id=:batchId AND e.eligibility_status='ELIGIBLE'
                  AND NOT EXISTS (SELECT 1 FROM bed_assignment a WHERE a.batch_id=e.batch_id AND a.student_id=e.student_id)
                """, Map.of("batchId", batchId)));
        metrics.put("recommendationAdoptionCount", scalar("SELECT COUNT(*) FROM recommendation_log WHERE batch_id=:batchId AND adopted=1", Map.of("batchId", batchId)));
        metrics.put("averageMatchScore", decimal("SELECT AVG(match_score) FROM bed_assignment WHERE batch_id=:batchId", Map.of("batchId", batchId)));
        metrics.put("minimumMatchScore", decimal("SELECT MIN(match_score) FROM bed_assignment WHERE batch_id=:batchId", Map.of("batchId", batchId)));
        metrics.put("roomChangeCount", scalar("SELECT COUNT(DISTINCT student_id) FROM room_change_request WHERE batch_id=:batchId AND request_status='EXECUTED'", Map.of("batchId", batchId)));
        metrics.put("exchangeCount", scalar("SELECT COUNT(DISTINCT student_id) FROM room_exchange_participant WHERE batch_id=:batchId AND participant_status='EXECUTED'", Map.of("batchId", batchId)));
        metrics.put("waitlistRequestCount", scalar("SELECT COUNT(DISTINCT student_id) FROM waitlist_entry WHERE batch_id=:batchId", Map.of("batchId", batchId)));
        metrics.put("waitlistAssignmentCount", scalar("SELECT COUNT(DISTINCT student_id) FROM waitlist_entry WHERE batch_id=:batchId AND entry_status='ASSIGNED'", Map.of("batchId", batchId)));
        long assigned = scalar("SELECT COUNT(*) FROM bed_assignment WHERE batch_id=:batchId", Map.of("batchId", batchId));
        long capacity = scalar("SELECT COALESCE(SUM(room.capacity),0) FROM batch_room_scope scope JOIN room ON room.id=scope.room_id WHERE scope.batch_id=:batchId", Map.of("batchId", batchId));
        metrics.put("bedUtilizationRate", capacity == 0 ? 0D : assigned * 1D / capacity);
        metrics.put("manualAdjustmentCount", scalar("SELECT COUNT(*) FROM audit_log WHERE resource_type='BED_ASSIGNMENT' AND action_type LIKE 'ASSIGNMENT_%' AND JSON_EXTRACT(after_data,'$.batchId')=:batchId", Map.of("batchId", batchId)));
        metrics.put("anomalyCount", scalar("SELECT COUNT(*) FROM operation_anomaly WHERE batch_id=:batchId", Map.of("batchId", batchId)));
        metrics.put("completionDurationSeconds", scalar("SELECT TIMESTAMPDIFF(SECOND,created_at,finished_at) FROM selection_batch WHERE id=:batchId", Map.of("batchId", batchId)));
        return metrics;
    }

    private Map<String, Object> dimensions(long batchId) {
        return Map.of(
                "batchId", batchId,
                "metricVersion", METRIC_VERSION,
                "privacyThreshold", 5,
                "sourceBasis", "BATCH_FINAL_RESULT");
    }

    private Map<String, Object> batch(long batchId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,batch_status FROM selection_batch WHERE id=:id FOR UPDATE", Map.of("id", batchId));
        if (rows.isEmpty()) throw new BusinessException("BATCH_NOT_FOUND", "批次不存在", HttpStatus.NOT_FOUND);
        return rows.getFirst();
    }

    private Map<String, Object> find(long batchId, String metricVersion) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id,batch_id,metric_version,metrics_json,dimensions_json,
                       source_basis,data_updated_at,immutable,created_at
                FROM batch_analytics_snapshot
                WHERE batch_id=:batchId AND metric_version=:metricVersion
                """, Map.of("batchId", batchId, "metricVersion", metricVersion));
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

    private double decimal(String sql, Map<String, ?> parameters) {
        Number value = jdbc.queryForObject(sql, parameters, Number.class);
        return value == null ? 0D : value.doubleValue();
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
