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
    private static final int PRIVACY_THRESHOLD = 5;

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
        AnalyticsFilter filter = request.normalized();
        StringBuilder sql = new StringBuilder("""
                SELECT snapshot.id, snapshot.batch_id, snapshot.metric_version,
                       snapshot.metrics_json, snapshot.dimensions_json,
                       snapshot.source_basis, snapshot.data_updated_at,
                       batch.batch_code, batch.batch_name,
                       YEAR(batch.start_at) AS academic_year
                FROM batch_analytics_snapshot snapshot
                JOIN selection_batch batch ON batch.id=snapshot.batch_id
                WHERE snapshot.immutable=1
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        append(sql, params, " AND YEAR(batch.start_at)=:academicYear", "academicYear", filter.academicYear());
        append(sql, params, " AND batch.id=:batchId", "batchId", filter.batchId());
        if (filter.majorId() != null || filter.gradeYear() != null || !filter.degreeLevel().isBlank()
                || !filter.studentCategory().isBlank() || filter.campusId() != null
                || filter.buildingId() != null || !filter.roomType().isBlank()) {
            sql.append("""
                     AND EXISTS (
                       SELECT 1
                       FROM batch_student_eligibility eligibility
                       JOIN student ON student.id=eligibility.student_id
                       LEFT JOIN major ON major.id=student.major_id
                       LEFT JOIN bed_assignment assignment
                         ON assignment.batch_id=eligibility.batch_id
                        AND assignment.student_id=student.id
                       LEFT JOIN room ON room.id=assignment.room_id
                       LEFT JOIN dormitory_floor floor ON floor.id=room.floor_id
                       LEFT JOIN dormitory_building building ON building.id=floor.building_id
                       WHERE eligibility.batch_id=batch.id
                    """);
            append(sql, params, " AND student.major_id=:majorId", "majorId", filter.majorId());
            append(sql, params, " AND student.grade_year=:gradeYear", "gradeYear", filter.gradeYear());
            append(sql, params, " AND student.degree_level=:degreeLevel", "degreeLevel", filter.degreeLevel());
            append(sql, params, " AND student.student_category=:studentCategory", "studentCategory", filter.studentCategory());
            append(sql, params, " AND building.campus_id=:campusId", "campusId", filter.campusId());
            append(sql, params, " AND building.id=:buildingId", "buildingId", filter.buildingId());
            append(sql, params, " AND room.room_type=:roomType", "roomType", filter.roomType());
            sql.append(")");
        }
        sql.append(" ORDER BY academic_year, snapshot.batch_id");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params).stream()
                .map(this::normalizeSnapshot)
                .toList();
        long sampleSize = filteredSampleSize(filter);
        boolean suppressed = sampleSize > 0 && sampleSize < PRIVACY_THRESHOLD;
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("filters", filter.asMap());
        response.put("sampleSize", sampleSize);
        response.put("privacyThreshold", PRIVACY_THRESHOLD);
        response.put("preferenceDimensionsSuppressed", suppressed);
        response.put("items", includeSeries ? rows : rows.stream().limit(1).toList());
        response.put("metricVersion", BatchAnalyticsSnapshotService.METRIC_VERSION);
        return response;
    }

    private long filteredSampleSize(AnalyticsFilter filter) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(DISTINCT student.id)
                FROM batch_student_eligibility eligibility
                JOIN student ON student.id=eligibility.student_id
                JOIN selection_batch batch ON batch.id=eligibility.batch_id
                LEFT JOIN bed_assignment assignment
                  ON assignment.batch_id=batch.id AND assignment.student_id=student.id
                LEFT JOIN room ON room.id=assignment.room_id
                LEFT JOIN dormitory_floor floor ON floor.id=room.floor_id
                LEFT JOIN dormitory_building building ON building.id=floor.building_id
                WHERE eligibility.eligibility_status='ELIGIBLE'
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        append(sql, params, " AND YEAR(batch.start_at)=:academicYear", "academicYear", filter.academicYear());
        append(sql, params, " AND batch.id=:batchId", "batchId", filter.batchId());
        append(sql, params, " AND student.major_id=:majorId", "majorId", filter.majorId());
        append(sql, params, " AND student.grade_year=:gradeYear", "gradeYear", filter.gradeYear());
        append(sql, params, " AND student.degree_level=:degreeLevel", "degreeLevel", filter.degreeLevel());
        append(sql, params, " AND student.student_category=:studentCategory", "studentCategory", filter.studentCategory());
        append(sql, params, " AND building.campus_id=:campusId", "campusId", filter.campusId());
        append(sql, params, " AND building.id=:buildingId", "buildingId", filter.buildingId());
        append(sql, params, " AND room.room_type=:roomType", "roomType", filter.roomType());
        Number value = jdbc.queryForObject(sql.toString(), params, Number.class);
        return value == null ? 0L : value.longValue();
    }

    private Map<String, Object> normalizeSnapshot(Map<String, Object> row) {
        Map<String, Object> normalized = new LinkedHashMap<>(row);
        normalized.put("metrics", parse(normalized.remove("metrics_json")));
        normalized.put("dimensions", parse(normalized.remove("dimensions_json")));
        return normalized;
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
        AnalyticsFilter normalized() {
            return new AnalyticsFilter(
                    academicYear,
                    batchId,
                    majorId,
                    gradeYear,
                    clean(degreeLevel),
                    clean(studentCategory),
                    campusId,
                    buildingId,
                    clean(roomType));
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
