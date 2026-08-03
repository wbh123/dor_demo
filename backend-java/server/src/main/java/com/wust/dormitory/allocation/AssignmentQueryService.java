package com.wust.dormitory.allocation;

import com.wust.dormitory.common.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AssignmentQueryService {
    private final NamedParameterJdbcTemplate jdbc;

    public AssignmentQueryService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> list(long batchId, String keyword) {
        Integer batchCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM selection_batch WHERE id=:batchId",
                Map.of("batchId", batchId),
                Integer.class
        );
        if (batchCount == null || batchCount == 0) {
            throw new BusinessException(
                    "BATCH_NOT_FOUND",
                    "选寝批次不存在",
                    HttpStatus.NOT_FOUND
            );
        }

        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        List<Map<String, Object>> assignments = jdbc.queryForList("""
                SELECT a.id AS assignment_id,
                       a.batch_id,
                       a.student_id,
                       s.student_number,
                       s.student_name,
                       s.gender,
                       m.major_code,
                       m.major_name,
                       a.bed_id,
                       bed.bed_code,
                       bed.bed_type,
                       r.id AS room_id,
                       r.room_number,
                       r.room_type,
                       b.id AS building_id,
                       b.building_code,
                       b.building_name,
                       a.team_id,
                       a.assignment_method,
                       a.assigned_at
                FROM bed_assignment a
                JOIN student s ON s.id=a.student_id
                JOIN major m ON m.id=s.major_id
                JOIN bed ON bed.id=a.bed_id
                JOIN room r ON r.id=bed.room_id
                JOIN dormitory_floor f ON f.id=r.floor_id
                JOIN dormitory_building b ON b.id=f.building_id
                WHERE a.batch_id=:batchId
                  AND (
                    :keyword=''
                    OR s.student_number LIKE :keywordLike
                    OR s.student_name LIKE :keywordLike
                    OR b.building_name LIKE :keywordLike
                    OR r.room_number LIKE :keywordLike
                  )
                ORDER BY s.student_number
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("keyword", normalizedKeyword)
                .addValue("keywordLike", "%" + normalizedKeyword + "%"));

        for (Map<String, Object> assignment : assignments) {
            assignment.put("availableBeds", availableBeds(
                    batchId,
                    String.valueOf(assignment.get("gender"))
            ));
        }
        return assignments;
    }

    private List<Map<String, Object>> availableBeds(long batchId, String gender) {
        return jdbc.queryForList("""
                SELECT bed.id AS bed_id,
                       bed.bed_code,
                       bed.bed_type,
                       r.id AS room_id,
                       r.room_number,
                       b.building_code,
                       b.building_name,
                       CONCAT(b.building_name, ' ', r.room_number, '-', bed.bed_code) AS display_name
                FROM bed
                JOIN room r ON r.id=bed.room_id
                JOIN dormitory_floor f ON f.id=r.floor_id
                JOIN dormitory_building b ON b.id=f.building_id
                LEFT JOIN bed_assignment occupied
                  ON occupied.batch_id=:batchId AND occupied.bed_id=bed.id
                WHERE bed.operational_status='ENABLED'
                  AND r.operational_status='ENABLED'
                  AND r.gender_restriction=:gender
                  AND occupied.id IS NULL
                  AND (
                    EXISTS (
                      SELECT 1 FROM batch_room_scope rs
                      WHERE rs.batch_id=:batchId AND rs.room_id=r.id
                    )
                    OR EXISTS (
                      SELECT 1 FROM batch_building_scope bs
                      WHERE bs.batch_id=:batchId AND bs.building_id=b.id
                    )
                  )
                  AND (
                    NOT EXISTS (
                      SELECT 1 FROM batch_bed_scope configured
                      WHERE configured.batch_id=:batchId
                    )
                    OR EXISTS (
                      SELECT 1 FROM batch_bed_scope allowed
                      WHERE allowed.batch_id=:batchId AND allowed.bed_id=bed.id
                    )
                  )
                ORDER BY b.building_code, f.floor_number, r.room_number, bed.position_index
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("gender", gender));
    }
}
