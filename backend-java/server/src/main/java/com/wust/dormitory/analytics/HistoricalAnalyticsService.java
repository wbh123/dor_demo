package com.wust.dormitory.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HistoricalAnalyticsService {
    static final int PRIVACY_THRESHOLD = 5;

    private final NamedParameterJdbcTemplate jdbc;
    private final FeatureAccessService featureAccessService;
    private final ObjectMapper objectMapper;

    public HistoricalAnalyticsService(
            NamedParameterJdbcTemplate jdbc,
            FeatureAccessService featureAccessService,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.featureAccessService = featureAccessService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> dashboard(AnalyticsFilter filter) {
        featureAccessService.require(FeatureCodes.P3_HISTORICAL_DASHBOARD);
        return result(filter, false);
    }

    public Map<String, Object> comparison(AnalyticsFilter filter) {
        featureAccessService.require(FeatureCodes.P3_CROSS_BATCH_COMPARISON);
        return result(filter, true);
    }

    public Map<String, Object> trend(AnalyticsFilter filter) {
        featureAccessService.require(FeatureCodes.P3_TREND_ANALYSIS);
        Map<String, Object> data = result(filter, true);
        data.put("trendOrder", "academicYear,batchId");
        return data;
    }

    private Map<String, Object> result(AnalyticsFilter request, boolean includeSeries) {
        AnalyticsFilter filter = request == null ? AnalyticsFilter.empty() : request.normalized();
        StringBuilder sql = new StringBuilder("""
                SELECT snapshot.id, snapshot.batch_id, snapshot.metric_version,
                       snapshot.metrics_json, snapshot.dimensions_json,
                       snapshot.source_basis, snapshot.data_updated_at,
                       batch.batch_code, batch.batch_name,
                       YEAR(batch.start_at) AS academic_year,
                       COUNT(fact.student_id) AS sample_size,
                       COALESCE(SUM(fact.self_selected),0) AS self_selection_count,
                       COALESCE(SUM(fact.team_selected),0) AS team_selection_count,
                       COALESCE(SUM(fact.unified_allocated),0) AS unified_allocation_count,
                       COALESCE(SUM(fact.unassigned),0) AS unassigned_count,
                       COALESCE(SUM(fact.recommendation_adopted),0) AS recommendation_adoption_count,
                       AVG(fact.match_score) AS average_match_score,
                       MIN(fact.match_score) AS minimum_match_score,
                       COALESCE(SUM(fact.room_changed),0) AS room_change_count,
                       COALESCE(SUM(fact.exchanged),0) AS exchange_count,
                       COALESCE(SUM(fact.waitlist_requested),0) AS waitlist_request_count,
                       COALESCE(SUM(fact.waitlist_assigned),0) AS waitlist_assignment_count,
                       COALESCE(SUM(fact.manual_adjusted),0) AS manual_adjustment_student_count
                FROM batch_analytics_snapshot snapshot
                JOIN selection_batch batch ON batch.id=snapshot.batch_id
                JOIN batch_analytics_student_fact fact ON fact.batch_id=batch.id
                WHERE snapshot.immutable=1
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        append(sql, params, " AND YEAR(batch.start_at)=:academicYear", "academicYear", filter.academicYear());
        append(sql, params, " AND batch.id=:batchId", "batchId", filter.batchId());
        append(sql, params, " AND fact.major_id=:majorId", "majorId", filter.majorId());
        append(sql, params, " AND fact.grade_year=:gradeYear", "gradeYear", filter.gradeYear());
        append(sql, params, " AND fact.degree_level=:degreeLevel", "degreeLevel", filter.degreeLevel());
        append(sql, params, " AND fact.student_category=:studentCategory", "studentCategory", filter.studentCategory());
        append(sql, params, " AND fact.campus_id=:campusId", "campusId", filter.campusId());
        append(sql, params, " AND fact.building_id=:buildingId", "buildingId", filter.buildingId());
        append(sql, params, " AND fact.room_type=:roomType", "roomType", filter.roomType());
        sql.append("""
                GROUP BY snapshot.id, snapshot.batch_id, snapshot.metric_version,
                         snapshot.metrics_json, snapshot.dimensions_json,
                         snapshot.source_basis, snapshot.data_updated_at,
                         batch.batch_code, batch.batch_name, YEAR(batch.start_at)
                ORDER BY academic_year, snapshot.batch_id
                """);

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params).stream()
                .map(this::normalizeSnapshot)
                .toList();
        long sampleSize = rows.stream()
                .mapToLong(row -> number(row.get("sampleSize")).longValue())
                .sum();
        boolean suppressed = sampleSize > 0 && sampleSize < PRIVACY_THRESHOLD;
        List<Map<String, Object>> visibleRows = includeSeries
                ? rows
                : rows.isEmpty() ? List.of() : List.of(rows.getLast());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("filters", filter.asMap());
        response.put("sampleSize", sampleSize);
        response.put("privacyThreshold", PRIVACY_THRESHOLD);
        response.put("preferenceDimensionsSuppressed", suppressed);
        response.put("items", visibleRows);
        response.put("metricVersion", BatchAnalyticsSnapshotService.METRIC_VERSION);
        response.put("sourceBasis", "IMMUTABLE_STUDENT_FACTS");
        return response;
    }

    private Map<String, Object> normalizeSnapshot(Map<String, Object> row) {
        Map<String, Object> normalized = new LinkedHashMap<>(row);
        Map<String, Object> metrics = new LinkedHashMap<>(parse(normalized.remove("metrics_json")));
        Map<String, Object> dimensions = new LinkedHashMap<>(parse(normalized.remove("dimensions_json")));
        long sampleSize = number(normalized.remove("sample_size")).longValue();
        boolean suppressed = sampleSize > 0 && sampleSize < PRIVACY_THRESHOLD;

        metrics.put("participantCount", sampleSize);
        metrics.put("selfSelectionCount", number(normalized.remove("self_selection_count")).longValue());
        metrics.put("teamSelectionCount", number(normalized.remove("team_selection_count")).longValue());
        metrics.put("unifiedAllocationCount", number(normalized.remove("unified_allocation_count")).longValue());
        metrics.put("unassignedCount", number(normalized.remove("unassigned_count")).longValue());
        metrics.put("roomChangeCount", number(normalized.remove("room_change_count")).longValue());
        metrics.put("exchangeCount", number(normalized.remove("exchange_count")).longValue());
        metrics.put("waitlistRequestCount", number(normalized.remove("waitlist_request_count")).longValue());
        metrics.put("waitlistAssignmentCount", number(normalized.remove("waitlist_assignment_count")).longValue());
        metrics.put("manualAdjustmentStudentCount", number(normalized.remove("manual_adjustment_student_count")).longValue());
        if (suppressed) {
            metrics.put("recommendationAdoptionCount", null);
            metrics.put("averageMatchScore", null);
            metrics.put("minimumMatchScore", null);
        } else {
            metrics.put("recommendationAdoptionCount", number(normalized.remove("recommendation_adoption_count")).longValue());
            metrics.put("averageMatchScore", nullableNumber(normalized.remove("average_match_score")));
            metrics.put("minimumMatchScore", nullableNumber(normalized.remove("minimum_match_score")));
        }
        dimensions.put("filteredSampleSize", sampleSize);
        dimensions.put("preferenceDimensionsSuppressed", suppressed);
        normalized.put("sampleSize", sampleSize);
        normalized.put("metrics", metrics);
        normalized.put("dimensions", dimensions);
        return normalized;
    }

    private Number number(Object value) {
        return value instanceof Number number ? number : 0L;
    }

    private Number nullableNumber(Object value) {
        return value instanceof Number number ? number : null;
    }

    private Map<String, Object> parse(Object value) {
        if (value == null) return Map.of();
        try {
            if (value instanceof String text) return objectMapper.readValue(text, new TypeReference<>() {});
            return objectMapper.convertValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return Map.of("parseError", true);
        }
    }

    private void append(
            StringBuilder sql,
            MapSqlParameterSource params,
            String clause,
            String name,
            Object value) {
        if (value == null || value instanceof String text && text.isBlank()) return;
        sql.append(clause);
        params.addValue(name, value);
    }

    public record AnalyticsFilter(
            Integer academicYear,
            Long batchId,
            Long majorId,
            Integer gradeYear,
            String degreeLevel,
            String studentCategory,
            Long campusId,
            Long buildingId,
            String roomType) {
        static AnalyticsFilter empty() {
            return new AnalyticsFilter(null, null, null, null, "", "", null, null, "");
        }

        AnalyticsFilter normalized() {
            return new AnalyticsFilter(
                    academicYear, batchId, majorId, gradeYear,
                    clean(degreeLevel), clean(studentCategory),
                    campusId, buildingId, clean(roomType));
        }

        Map<String, Object> asMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("academicYear", academicYear);
            values.put("batchId", batchId);
            values.put("majorId", majorId);
            values.put("gradeYear", gradeYear);
            values.put("degreeLevel", degreeLevel);
            values.put("studentCategory", studentCategory);
            values.put("campusId", campusId);
            values.put("buildingId", buildingId);
            values.put("roomType", roomType);
            return values;
        }

        private static String clean(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
