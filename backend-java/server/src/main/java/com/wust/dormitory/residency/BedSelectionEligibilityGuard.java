package com.wust.dormitory.residency;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BedSelectionEligibilityGuard {
    private final NamedParameterJdbcTemplate jdbc;
    private final ResidencyPolicyService policy;

    public BedSelectionEligibilityGuard(
            NamedParameterJdbcTemplate jdbc,
            ResidencyPolicyService policy) {
        this.jdbc = jdbc;
        this.policy = policy;
    }

    public void requirePersonalAllowed(long batchId, long studentId, long bedId) {
        Map<String, Object> batch = policy.batch(batchId);
        if (!"BED".equals(String.valueOf(batch.get("selection_mode")))) {
            throw new BusinessException(
                    "BATCH_SELECTION_MODE_MISMATCH",
                    "当前批次不是选择床位模式",
                    HttpStatus.CONFLICT);
        }
        policy.requireBatchEligibility(batchId, studentId);
        policy.requireBedInBatch(batchId, bedId);
        Map<String, Object> room = roomForBed(bedId);
        policy.requireRoomLockedByBatch(batchId, number(room, "id"));
        policy.requireStudentEligibleForRoom(policy.student(studentId), batch, room);
        if (policy.unknownBedResidentCount(number(room, "id")) > 0) {
            throw new BusinessException(
                    "ROOM_BED_MAPPING_REQUIRED",
                    "该寝室仍有在住学生未确认实际床位，暂不能开放选床",
                    HttpStatus.CONFLICT);
        }
        policy.requireNoActiveResidency(studentId);
    }

    public void requireTeamAllowed(
            long batchId,
            long teamId,
            List<Long> bedIds,
            CurrentUser leader) {
        if (bedIds == null || bedIds.isEmpty()) {
            throw new BusinessException("TEAM_BEDS_REQUIRED", "请选择队伍所需床位");
        }
        Map<String, Object> batch = policy.batch(batchId);
        if (!"BED".equals(String.valueOf(batch.get("selection_mode")))) {
            throw new BusinessException(
                    "BATCH_SELECTION_MODE_MISMATCH",
                    "当前批次不是选择床位模式",
                    HttpStatus.CONFLICT);
        }
        Map<String, Object> team = team(batchId, teamId, leader.studentId());
        List<Map<String, Object>> members = members(teamId);
        if (members.size() != bedIds.size()) {
            throw new BusinessException(
                    "TEAM_BED_COUNT_MISMATCH",
                    "床位数量必须等于当前锁定队伍人数",
                    HttpStatus.CONFLICT);
        }

        Set<Long> roomIds = new LinkedHashSet<>();
        for (Long bedId : bedIds) {
            policy.requireBedInBatch(batchId, bedId);
            roomIds.add(number(roomForBed(bedId), "id"));
        }
        if (roomIds.size() != 1) {
            throw new BusinessException(
                    "TEAM_BEDS_NOT_IN_SAME_ROOM",
                    "队伍床位必须属于同一寝室",
                    HttpStatus.CONFLICT);
        }
        long roomId = roomIds.iterator().next();
        Map<String, Object> room = policy.room(roomId, false);
        policy.requireRoomLockedByBatch(batchId, roomId);
        if (policy.unknownBedResidentCount(roomId) > 0) {
            throw new BusinessException(
                    "ROOM_BED_MAPPING_REQUIRED",
                    "该寝室仍有在住学生未确认实际床位，暂不能开放选床",
                    HttpStatus.CONFLICT);
        }

        long categoryCount = members.stream()
                .map(member -> String.valueOf(member.get("student_category")))
                .distinct()
                .count();
        boolean separate = ((Number) batch.get("separate_student_categories")).intValue() == 1;
        if (separate && categoryCount > 1) {
            throw new BusinessException(
                    "TEAM_STUDENT_CATEGORY_MISMATCH",
                    "当前批次要求国内生与国际生分开选寝，混合类别队伍不能选床",
                    HttpStatus.CONFLICT);
        }
        if (categoryCount > 1 && !"MIXED".equals(String.valueOf(room.get("resident_scope")))) {
            throw new BusinessException(
                    "TEAM_STUDENT_CATEGORY_MISMATCH",
                    "国内生与国际生混合队伍只能选择混住宿舍",
                    HttpStatus.CONFLICT);
        }
        for (Map<String, Object> member : members) {
            long studentId = number(member, "student_id");
            policy.requireBatchEligibility(batchId, studentId);
            policy.requireStudentEligibleForRoom(policy.student(studentId), batch, room);
            policy.requireNoActiveResidency(studentId);
        }
        policy.requireRoomCapacity(roomId, members.size());
        if (((Number) team.get("member_count")).intValue() != members.size()) {
            throw new BusinessException("TEAM_MEMBERS_CHANGED", "队伍成员状态已经变化，请刷新后重试");
        }
    }

    private Map<String, Object> roomForBed(long bedId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT r.id, r.room_number, r.room_type, r.capacity,
                       r.gender_restriction, r.resident_scope,
                       r.operational_status, r.state_version,
                       f.floor_number, b.id AS building_id,
                       b.building_code, b.building_name
                FROM bed target_bed
                JOIN room r ON r.id=target_bed.room_id
                JOIN dormitory_floor f ON f.id=r.floor_id
                JOIN dormitory_building b ON b.id=f.building_id
                WHERE target_bed.id=:bedId
                """, Map.of("bedId", bedId));
        if (rows.isEmpty()) {
            throw new BusinessException("BED_NOT_FOUND", "床位不存在", HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    private Map<String, Object> team(long batchId, long teamId, long leaderStudentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT t.id, t.batch_id, t.leader_student_id, t.team_status,
                       (SELECT COUNT(*) FROM selection_team_member tm
                        WHERE tm.team_id=t.id AND tm.member_status='LOCKED') AS member_count
                FROM selection_team t
                WHERE t.id=:teamId AND t.batch_id=:batchId
                  AND t.leader_student_id=:leaderStudentId
                  AND t.team_status='LOCKED'
                """, new MapSqlParameterSource()
                .addValue("teamId", teamId)
                .addValue("batchId", batchId)
                .addValue("leaderStudentId", leaderStudentId));
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "TEAM_NOT_READY",
                    "只有锁定队伍的队长可以选择床位",
                    HttpStatus.CONFLICT);
        }
        return rows.getFirst();
    }

    private List<Map<String, Object>> members(long teamId) {
        return jdbc.queryForList("""
                SELECT tm.student_id, s.gender, s.student_category
                FROM selection_team_member tm
                JOIN student s ON s.id=tm.student_id
                WHERE tm.team_id=:teamId AND tm.member_status='LOCKED'
                ORDER BY tm.member_role='LEADER' DESC, tm.id
                """, Map.of("teamId", teamId));
    }

    private long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }
}
