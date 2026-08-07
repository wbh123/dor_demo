package com.wust.dormitory.notification;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class NotificationRecipientResolver {
    private final NamedParameterJdbcTemplate jdbc;

    public NotificationRecipientResolver(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Long> resolve(RecipientCriteria criteria) {
        RecipientCriteria normalized = criteria == null
                ? RecipientCriteria.empty()
                : criteria.normalized();
        if (!normalized.studentIds().isEmpty()) {
            return normalized.studentIds();
        }
        StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT student.id
                FROM student
                LEFT JOIN major ON major.id=student.major_id
                LEFT JOIN batch_student_eligibility eligibility
                  ON eligibility.student_id=student.id
                LEFT JOIN room_assignment residency
                  ON residency.student_id=student.id
                 AND residency.assignment_status='ACTIVE'
                LEFT JOIN room ON room.id=residency.room_id
                LEFT JOIN dormitory_floor floor ON floor.id=room.floor_id
                LEFT JOIN dormitory_building building ON building.id=floor.building_id
                WHERE student.student_status='ACTIVE'
                """);
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        appendIn(sql, parameters, "eligibility.batch_id", "batchIds", normalized.batchIds());
        appendIn(sql, parameters, "student.major_id", "majorIds", normalized.majorIds());
        appendIn(sql, parameters, "student.grade_year", "gradeYears", normalized.gradeYears());
        appendIn(sql, parameters, "student.degree_level", "degreeLevels", normalized.degreeLevels());
        appendIn(sql, parameters, "student.student_category", "studentCategories", normalized.studentCategories());
        appendIn(sql, parameters, "building.id", "buildingIds", normalized.buildingIds());
        if (normalized.unselectedOnly()) {
            sql.append("""
                     AND NOT EXISTS (
                       SELECT 1 FROM bed_assignment assignment
                       WHERE assignment.student_id=student.id
                    """);
            if (!normalized.batchIds().isEmpty()) {
                sql.append(" AND assignment.batch_id IN (:unselectedBatchIds)");
                parameters.addValue("unselectedBatchIds", normalized.batchIds());
            }
            sql.append(" )");
        }
        if (normalized.pendingReviewOnly()) {
            sql.append(" AND student.selection_review_status='PENDING'");
        }
        sql.append(" ORDER BY student.id");
        return jdbc.queryForList(sql.toString(), parameters, Long.class);
    }

    private void appendIn(
            StringBuilder sql,
            MapSqlParameterSource parameters,
            String column,
            String name,
            List<?> values) {
        if (values == null || values.isEmpty()) return;
        sql.append(" AND ").append(column).append(" IN (:").append(name).append(")");
        parameters.addValue(name, values);
    }

    public record RecipientCriteria(
            List<Long> studentIds,
            Long batchId,
            Long majorId,
            Integer gradeYear,
            String degreeLevel,
            String studentCategory,
            Long buildingId,
            boolean unselectedOnly,
            boolean pendingReviewOnly,
            List<Long> batchIds,
            List<Long> majorIds,
            List<Integer> gradeYears,
            List<String> degreeLevels,
            List<String> studentCategories,
            List<Long> buildingIds) {

        public RecipientCriteria(
                List<Long> studentIds,
                Long batchId,
                Long majorId,
                Integer gradeYear,
                String degreeLevel,
                String studentCategory,
                Long buildingId,
                boolean unselectedOnly,
                boolean pendingReviewOnly) {
            this(
                    studentIds, batchId, majorId, gradeYear,
                    degreeLevel, studentCategory, buildingId,
                    unselectedOnly, pendingReviewOnly,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }

        static RecipientCriteria empty() {
            return new RecipientCriteria(
                    List.of(), null, null, null, "", "", null,
                    false, false,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }

        RecipientCriteria normalized() {
            return new RecipientCriteria(
                    normalizeLongs(studentIds, null),
                    batchId,
                    majorId,
                    gradeYear,
                    clean(degreeLevel),
                    clean(studentCategory),
                    buildingId,
                    unselectedOnly,
                    pendingReviewOnly,
                    normalizeLongs(batchIds, batchId),
                    normalizeLongs(majorIds, majorId),
                    normalizeIntegers(gradeYears, gradeYear),
                    normalizeStrings(degreeLevels, degreeLevel),
                    normalizeStrings(studentCategories, studentCategory),
                    normalizeLongs(buildingIds, buildingId));
        }

        private static List<Long> normalizeLongs(List<Long> values, Long legacyValue) {
            Set<Long> unique = new LinkedHashSet<>(values == null ? List.of() : values);
            if (legacyValue != null) unique.add(legacyValue);
            unique.removeIf(value -> value == null || value <= 0);
            return unique.stream().sorted().toList();
        }

        private static List<Integer> normalizeIntegers(List<Integer> values, Integer legacyValue) {
            Set<Integer> unique = new LinkedHashSet<>(values == null ? List.of() : values);
            if (legacyValue != null) unique.add(legacyValue);
            unique.removeIf(value -> value == null || value <= 0);
            return unique.stream().sorted().toList();
        }

        private static List<String> normalizeStrings(List<String> values, String legacyValue) {
            Set<String> unique = new LinkedHashSet<>();
            if (values != null) {
                for (String value : values) {
                    String normalized = clean(value);
                    if (!normalized.isEmpty()) unique.add(normalized);
                }
            }
            String normalizedLegacy = clean(legacyValue);
            if (!normalizedLegacy.isEmpty()) unique.add(normalizedLegacy);
            return List.copyOf(unique);
        }

        private static String clean(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
