package com.wust.dormitory.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.analytics.mapper.BatchAnalyticsSnapshotMapper;
import com.wust.dormitory.common.error.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
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

    private final BatchAnalyticsSnapshotMapper mapper;
    private final ObjectMapper objectMapper;

    public BatchAnalyticsSnapshotService(
            BatchAnalyticsSnapshotMapper mapper,
            ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> snapshot(long batchId) {
        Map<String, Object> batch = mapper.findBatch(batchId);
        if (batch == null || batch.isEmpty()) {
            throw new BusinessException("BATCH_NOT_FOUND", "批次不存在", HttpStatus.NOT_FOUND);
        }
        if (!"FINISHED".equals(String.valueOf(batch.get("batch_status")))) {
            throw new BusinessException(
                    "BATCH_ANALYTICS_NOT_FINAL",
                    "只有已完成批次可以生成不可变历史快照",
                    HttpStatus.CONFLICT);
        }
        Map<String, Object> existing = find(batchId);
        if (existing != null) return existing;

        LocalDateTime snapshotAt = LocalDateTime.now();
        mapper.insertStudentFacts(batchId, snapshotAt);
        Map<String, Object> metrics = calculate(batchId);
        Map<String, Object> dimensions = dimensions(batchId);
        try {
            mapper.insertSnapshot(
                    batchId,
                    METRIC_VERSION,
                    json(metrics),
                    json(dimensions),
                    snapshotAt);
        } catch (DuplicateKeyException ignored) {
            return find(batchId);
        }
        return find(batchId);
    }

    @Scheduled(fixedDelayString = "${app.analytics.snapshot-delay-ms:30000}")
    public void snapshotMissingFinishedBatches() {
        mapper.findMissingFinishedBatchIds(METRIC_VERSION, 20).forEach(this::snapshot);
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
            String code, String nameZhCn, String nameEnUs, String timeRange, LocalDateTime updatedAt) {
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

    private Map<String, Object> calculate(long batchId) {
        Map<String, Object> row = mapper.findAggregateMetrics(batchId);
        if (row == null) row = Map.of();
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("studentTotal", longValue(row.get("student_total")));
        metrics.put("participantCount", longValue(row.get("participant_count")));
        metrics.put("selfSelectionCount", longValue(row.get("self_selection_count")));
        metrics.put("teamSelectionCount", longValue(row.get("team_selection_count")));
        metrics.put("unifiedAllocationCount", longValue(row.get("unified_allocation_count")));
        metrics.put("unassignedCount", longValue(row.get("unassigned_count")));
        metrics.put("recommendationAdoptionCount", longValue(row.get("recommendation_adoption_count")));
        metrics.put("averageMatchScore", doubleValue(row.get("average_match_score")));
        metrics.put("minimumMatchScore", doubleValue(row.get("minimum_match_score")));
        metrics.put("roomChangeCount", longValue(row.get("room_change_count")));
        metrics.put("exchangeCount", longValue(row.get("exchange_count")));
        metrics.put("waitlistRequestCount", longValue(row.get("waitlist_request_count")));
        metrics.put("waitlistAssignmentCount", longValue(row.get("waitlist_assignment_count")));
        metrics.put("bedUtilizationRate", doubleValue(row.get("bed_utilization_rate")));
        metrics.put("manualAdjustmentCount", longValue(row.get("manual_adjustment_count")));
        metrics.put("anomalyCount", 0L);
        metrics.put("completionDurationSeconds", longValue(row.get("completion_duration_seconds")));
        return metrics;
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

    private Map<String, Object> find(long batchId) {
        Map<String, Object> row = mapper.findSnapshot(batchId, METRIC_VERSION);
        if (row == null || row.isEmpty()) return null;
        Map<String, Object> result = new LinkedHashMap<>(row);
        result.put("metrics", parse(result.remove("metrics_json")));
        result.put("dimensions", parse(result.remove("dimensions_json")));
        return result;
    }

    private long longValue(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private double doubleValue(Object value) {
        return value == null ? 0D : ((Number) value).doubleValue();
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
                return objectMapper.readValue(text, new TypeReference<>() { });
            }
            return objectMapper.convertValue(value, new TypeReference<>() { });
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return Map.of("parseError", true);
        }
    }
}
