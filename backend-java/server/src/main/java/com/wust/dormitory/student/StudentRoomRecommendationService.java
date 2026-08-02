package com.wust.dormitory.student;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.matching.MatchingService;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class StudentRoomRecommendationService {
    private final NamedParameterJdbcTemplate jdbc;
    private final MatchingService matchingService;
    private final StudentService studentService;

    public StudentRoomRecommendationService(
            NamedParameterJdbcTemplate jdbc,
            MatchingService matchingService,
            StudentService studentService) {
        this.jdbc = jdbc;
        this.matchingService = matchingService;
        this.studentService = studentService;
    }

    public List<Map<String, Object>> rooms(long batchId, CurrentUser user) {
        String gender = studentGender(user.studentId());
        requireAccessibleBatch(batchId, user.studentId());
        String feature = featureJson(batchId, user.studentId());

        List<Map<String, Object>> rooms = jdbc.queryForList("""
                SELECT room.id, building.building_name, floor.floor_number,
                       room.room_number, room.room_type, room.capacity,
                       room.gender_restriction, room.state_version,
                       COUNT(bed.id) AS bed_count,
                       SUM(bed.operational_status='ENABLED') AS enabled_bed_count,
                       COUNT(assignment.id) AS assigned_count
                FROM room
                JOIN dormitory_floor floor ON floor.id=room.floor_id
                JOIN dormitory_building building ON building.id=floor.building_id
                JOIN bed ON bed.room_id=room.id
                LEFT JOIN bed_assignment assignment
                  ON assignment.batch_id=:batchId AND assignment.bed_id=bed.id
                WHERE room.operational_status='ENABLED'
                  AND bed.operational_status='ENABLED'
                  AND room.gender_restriction=:gender
                  AND (
                    EXISTS (
                      SELECT 1 FROM batch_room_scope room_scope
                      WHERE room_scope.batch_id=:batchId
                        AND room_scope.room_id=room.id
                    )
                    OR EXISTS (
                      SELECT 1 FROM batch_building_scope building_scope
                      WHERE building_scope.batch_id=:batchId
                        AND building_scope.building_id=building.id
                    )
                  )
                GROUP BY room.id, building.building_name, floor.floor_number,
                         room.room_number, room.room_type, room.capacity,
                         room.gender_restriction, room.state_version
                HAVING COUNT(assignment.id) < COUNT(bed.id)
                ORDER BY building.building_name, floor.floor_number, room.room_number
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("gender", gender));

        for (Map<String, Object> room : rooms) {
            long roomId = ((Number) room.get("id")).longValue();
            List<String> roommateFeatures = jdbc.query("""
                    SELECT feature.feature_vector_json
                    FROM bed_assignment assignment
                    JOIN bed ON bed.id=assignment.bed_id
                    JOIN student_feature feature
                      ON feature.batch_id=assignment.batch_id
                     AND feature.student_id=assignment.student_id
                    WHERE assignment.batch_id=:batchId
                      AND bed.room_id=:roomId
                    """, new MapSqlParameterSource()
                    .addValue("batchId", batchId)
                    .addValue("roomId", roomId),
                    (resultSet, rowNumber) -> resultSet.getString(1));
            MatchingService.MatchResult match = matchingService.roomScore(
                    batchId,
                    feature,
                    roommateFeatures);
            int availableCount = ((Number) room.get("enabled_bed_count")).intValue()
                    - ((Number) room.get("assigned_count")).intValue();
            room.put("availableCount", availableCount);
            room.put("matchScore", match.score());
            room.put("matches", match.matches());
            room.put("warnings", match.warnings());
            room.put("recommendationReasons", match.recommendationReasons());
            room.put("conflictReasons", match.conflictReasons());
            room.put("dimensionCount", match.dimensionCount());
        }
        rooms.sort(Comparator.comparingDouble(
                room -> -((Number) room.get("matchScore")).doubleValue()));
        return rooms;
    }

    public Map<String, Object> randomRecommendation(long batchId, CurrentUser user) {
        for (Map<String, Object> candidate : rooms(batchId, user)) {
            long roomId = ((Number) candidate.get("id")).longValue();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> beds = (List<Map<String, Object>>) studentService
                    .room(batchId, roomId, user)
                    .get("beds");
            for (Map<String, Object> bed : beds) {
                if ("AVAILABLE".equals(bed.get("status"))) {
                    return Map.of(
                            "room", candidate,
                            "bed", bed,
                            "explanation", "从高匹配房间中选择当前可用床位，确认前不会形成最终分配");
                }
            }
        }
        throw new BusinessException(
                "NO_AVAILABLE_BED",
                "当前没有符合条件的可用床位",
                HttpStatus.CONFLICT);
    }

    private String studentGender(long studentId) {
        List<String> rows = jdbc.query(
                "SELECT gender FROM student WHERE id=:studentId",
                Map.of("studentId", studentId),
                (resultSet, rowNumber) -> resultSet.getString(1));
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "STUDENT_NOT_FOUND",
                    "学生档案不存在",
                    HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    private void requireAccessibleBatch(long batchId, long studentId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM selection_batch batch
                JOIN batch_student_eligibility eligibility
                  ON eligibility.batch_id=batch.id
                 AND eligibility.student_id=:studentId
                WHERE batch.id=:batchId
                  AND eligibility.eligibility_status='ELIGIBLE'
                  AND batch.batch_status IN ('PUBLISHED','OPEN','PAUSED')
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("studentId", studentId), Integer.class);
        if (count == null || count != 1) {
            throw new BusinessException(
                    "BATCH_NOT_ACCESSIBLE",
                    "当前选寝活动不可访问",
                    HttpStatus.FORBIDDEN);
        }
    }

    private String featureJson(long batchId, long studentId) {
        List<String> rows = jdbc.query("""
                SELECT feature_vector_json
                FROM student_feature
                WHERE batch_id=:batchId AND student_id=:studentId
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("studentId", studentId),
                (resultSet, rowNumber) -> resultSet.getString(1));
        return rows.isEmpty() ? null : rows.getFirst();
    }
}
