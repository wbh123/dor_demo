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
        RecipientCriteria normalized = criteria.normalized();
        if (!normalized.studentIds().isEmpty()) {
            return normalized.studentIds().stream().distinct().sorted().toList();
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
        append(sql, parameters, " AND eligibility.batch_id=:batchId", "batchId", normalized.batchId());
        append(sql, parameters, " AND student.major_id=:majorId", "majorId", normalized.majorId());
        append(sql, parameters, " AND student.grade_year=:gradeYear", "gradeYear", normalized.gradeYear());
        append(sql, parameters, " AND student.degree_level=:degreeLevel", "degreeLevel", normalized.degreeLevel());
        append(sql, parameters, " AND student.student_category=:studentCategory", "studentCategory", normalized.studentCategory());
        append(sql, parameters, " AND building.id=:buildingId", "buildingId", normalized.buildingId());
        if (normalized.unselectedOnly()) {
            sql.append("""
                     AND NOT EXISTS (
                       SELECT 1 FROM bed_assignment assignment
                       WHERE assignment.student_id=student.id
                         AND (:batchId IS NULL OR assignment.batch_id=:batchId)
                     )
                    """);
            if (!parameters.hasValue("batchId")) parameters.addValue("batchId", null);
        }
        if (normalized.pendingReviewOnly()) {
            sql.append(" AND student.selection_review_status='PENDING'");
        }
        sql.append(" ORDER BY student.id");
        return jdbc.queryForList(sql.toString(), parameters, Long.class);
    }

    private void append(
            StringBuilder sql,
            MapSqlParameterSource parameters,
            String clause,
            String name,
            Object value) {
        if (value == null || value instanceof String text && text.isBlank()) return;
        sql.append(clause);
        parameters.addValue(name, value);
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
            boolean pendingReviewOnly) {
        RecipientCriteria normalized() {
            Set<Long> unique = new LinkedHashSet<>(studentIds == null ? List.of() : studentIds);
            unique.removeIf(id -> id == null || id <= 0);
            return new RecipientCriteria(
                    List.copyOf(unique), batchId, majorId, gradeYear,
                    clean(degreeLevel), clean(studentCategory), buildingId,
                    unselectedOnly, pendingReviewOnly);
        }

        private static String clean(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
