package com.wust.dormitory.student;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.matching.MatchingService;
import com.wust.dormitory.residency.ResidencyPolicyService;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudentRoomRecommendationService {
    private final NamedParameterJdbcTemplate jdbc;
    private final MatchingService matchingService;
    private final ResidencyPolicyService policy;

    public StudentRoomRecommendationService(
            NamedParameterJdbcTemplate jdbc,
            MatchingService matchingService,
            ResidencyPolicyService policy) {
        this.jdbc = jdbc;
        this.matchingService = matchingService;
        this.policy = policy;
    }

    public List<Map<String, Object>> rooms(long batchId, CurrentUser user) {
        requireAccessibleBatch(batchId, user.studentId());
        Map<String, Object> batch = policy.batch(batchId);
        Map<String, Object> student = policy.student(user.studentId());
        String feature = featureJson(batchId, user.studentId());
        String mode = String.valueOf(batch.get("selection_mode"));
        List<Map<String, Object>> result = new ArrayList<>();

        for (Long roomId : policy.roomIdsForBatch(batchId)) {
            Map<String, Object> room = policy.room(roomId, false);
            try {
                policy.requireStudentEligibleForRoom(student, batch, room);
                policy.requireRoomLockedByBatch(batchId, roomId);
            } catch (BusinessException ignored) {
                continue;
            }
            int activeResidents = policy.activeResidentCount(roomId);
            int unknownBedResidents = policy.unknownBedResidentCount(roomId);
            int available = "BED".equals(mode)
                    ? (unknownBedResidents == 0 ? policy.availableBedCount(batchId, roomId) : 0)
                    : policy.availableCapacity(roomId);
            if (available <= 0) {
                continue;
            }

            List<String> roommateFeatures = jdbc.query("""
                    SELECT sf.feature_vector_json
                    FROM room_assignment ra
                    JOIN student_feature sf
                      ON sf.student_id=ra.student_id
                     AND sf.batch_id=:batchId
                    WHERE ra.room_id=:roomId
                      AND ra.assignment_status='ACTIVE'
                    ORDER BY ra.assigned_at
                    """, new MapSqlParameterSource()
                    .addValue("batchId", batchId)
                    .addValue("roomId", roomId),
                    (rs, rowNum) -> rs.getString(1));
            MatchingService.MatchResult match = matchingService.roomScore(
                    batchId,
                    feature,
                    roommateFeatures);
            Map<String, Object> view = new LinkedHashMap<>(room);
            view.put("selectionMode", mode);
            view.put("activeResidentCount", activeResidents);
            view.put("confirmedBedCount", activeResidents - unknownBedResidents);
            view.put("unconfirmedBedCount", unknownBedResidents);
            view.put("bedMappingComplete", unknownBedResidents == 0);
            view.put("availableCount", available);
            view.put("matchScore", match.score());
            view.put("matches", match.matches());
            view.put("warnings", match.warnings());
            view.put("recommendationReasons", match.recommendationReasons());
            view.put("conflictReasons", match.conflictReasons());
            view.put("dimensionCount", match.dimensionCount());
            view.put("selectionHint", "ROOM".equals(mode)
                    ? "选择后仅确定寝室，具体床位由寝室成员自行协商"
                    : "进入寝室后选择当前批次范围内的真实可用床位");
            result.add(view);
        }
        result.sort(Comparator.comparingDouble(
                room -> -((Number) room.get("matchScore")).doubleValue()));
        return result;
    }

    public Map<String, Object> room(long batchId, long roomId, CurrentUser user) {
        return rooms(batchId, user).stream()
                .filter(room -> ((Number) room.get("id")).longValue() == roomId)
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "ROOM_NOT_CANDIDATE",
                        "该寝室当前不符合你的选择条件或已经没有剩余名额",
                        HttpStatus.FORBIDDEN));
    }

    public Map<String, Object> randomRecommendation(long batchId, CurrentUser user) {
        Map<String, Object> batch = policy.batch(batchId);
        List<Map<String, Object>> candidates = rooms(batchId, user);
        if (candidates.isEmpty()) {
            throw new BusinessException(
                    "NO_AVAILABLE_ROOM",
                    "当前没有符合条件的可用寝室",
                    HttpStatus.CONFLICT);
        }
        Map<String, Object> room = candidates.getFirst();
        if ("ROOM".equals(String.valueOf(batch.get("selection_mode")))) {
            return Map.of(
                    "selectionMode", "ROOM",
                    "room", room,
                    "explanation", "从符合性别、学生类别和容量条件的寝室中推荐匹配度较高的寝室；不会分配具体床位");
        }
        long roomId = ((Number) room.get("id")).longValue();
        List<Map<String, Object>> beds = jdbc.queryForList("""
                SELECT target_bed.id, target_bed.bed_code,
                       target_bed.bed_type, target_bed.position_index
                FROM bed target_bed
                JOIN room target_room ON target_room.id=target_bed.room_id
                JOIN dormitory_floor target_floor ON target_floor.id=target_room.floor_id
                WHERE target_bed.room_id=:roomId
                  AND target_bed.operational_status='ENABLED'
                  AND (
                      EXISTS (
                          SELECT 1 FROM batch_bed_scope scope
                          WHERE scope.batch_id=:batchId AND scope.bed_id=target_bed.id
                      )
                      OR EXISTS (
                          SELECT 1 FROM batch_room_scope scope
                          WHERE scope.batch_id=:batchId AND scope.room_id=target_room.id
                      )
                      OR EXISTS (
                          SELECT 1 FROM batch_building_scope scope
                          WHERE scope.batch_id=:batchId
                            AND scope.building_id=target_floor.building_id
                      )
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM room_assignment ra
                      WHERE ra.bed_id=target_bed.id AND ra.assignment_status='ACTIVE'
                  )
                ORDER BY target_bed.position_index
                LIMIT 1
                """, new MapSqlParameterSource()
                .addValue("roomId", roomId)
                .addValue("batchId", batchId));
        if (beds.isEmpty()) {
            throw new BusinessException(
                    "NO_AVAILABLE_BED",
                    "推荐寝室当前没有属于本批次的真实可用床位",
                    HttpStatus.CONFLICT);
        }
        return Map.of(
                "selectionMode", "BED",
                "room", room,
                "bed", beds.getFirst(),
                "explanation", "从符合条件的寝室中推荐匹配度较高的真实可用床位，确认前不会形成最终分配");
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
                (rs, rowNum) -> rs.getString(1));
        return rows.isEmpty() ? null : rows.getFirst();
    }
}
