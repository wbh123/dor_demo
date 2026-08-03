package com.wust.dormitory.residency;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RoomSelectionService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ResidencyPolicyService policy;
    private final ResidencyService residencyService;

    public RoomSelectionService(
            NamedParameterJdbcTemplate jdbc,
            ResidencyPolicyService policy,
            ResidencyService residencyService) {
        this.jdbc = jdbc;
        this.policy = policy;
        this.residencyService = residencyService;
    }

    @Transactional
    public Map<String, Object> selectPersonal(
            long batchId,
            long roomId,
            CurrentUser user) {
        Map<String, Object> batch = requireRoomModeOpen(batchId);
        long studentId = user.studentId();
        policy.requireBatchEligibility(batchId, studentId);
        policy.requireRoomInBatch(batchId, roomId);
        policy.requireRoomLockedByBatch(batchId, roomId);
        requireNoActiveTeam(batchId, studentId);
        Map<String, Object> student = policy.student(studentId);
        Map<String, Object> room = policy.room(roomId, true);
        policy.requireStudentEligibleForRoom(student, batch, room);
        policy.requireRoomCapacity(roomId, 1);
        Map<String, Object> residency = residencyService.assign(
                studentId,
                roomId,
                null,
                batchId,
                null,
                "ROOM",
                "ROOM_SELECT",
                "学生选择寝室；具体床位由入住学生自行协商",
                user);
        return Map.of(
                "selectionMode", "ROOM",
                "roomAssigned", true,
                "bedAssigned", false,
                "message", "寝室选择成功，具体床位由寝室成员入住后自行协商",
                "residency", residency);
    }

    @Transactional
    public Map<String, Object> selectTeam(
            long batchId,
            long teamId,
            long roomId,
            CurrentUser user) {
        Map<String, Object> batch = requireRoomModeOpen(batchId);
        Map<String, Object> team = requireLockedLeaderTeam(batchId, teamId, user.studentId());
        List<Map<String, Object>> members = jdbc.queryForList("""
                SELECT tm.student_id, tm.member_role,
                       s.student_number, s.student_name, s.gender,
                       s.student_category
                FROM selection_team_member tm
                JOIN student s ON s.id=tm.student_id
                WHERE tm.team_id=:teamId AND tm.member_status='LOCKED'
                ORDER BY tm.member_role='LEADER' DESC, tm.id
                FOR UPDATE
                """, Map.of("teamId", teamId));
        if (members.isEmpty()) {
            throw new BusinessException("TEAM_MEMBERS_REQUIRED", "锁定队伍没有有效成员");
        }
        if (members.size() > ((Number) batch.get("team_max_size")).intValue()) {
            throw new BusinessException("TEAM_SIZE_INVALID", "队伍人数超过批次上限");
        }

        long genderCount = members.stream().map(row -> row.get("gender")).distinct().count();
        if (genderCount != 1) {
            throw new BusinessException("TEAM_GENDER_MISMATCH", "队伍成员性别必须一致");
        }
        long categoryCount = members.stream().map(row -> row.get("student_category")).distinct().count();
        boolean separate = ((Number) batch.get("separate_student_categories")).intValue() == 1;
        if (separate && categoryCount != 1) {
            throw new BusinessException(
                    "TEAM_STUDENT_CATEGORY_MISMATCH",
                    "当前批次要求国内生与国际生分开选寝，混合类别队伍不能选择寝室",
                    HttpStatus.CONFLICT);
        }

        policy.requireRoomInBatch(batchId, roomId);
        policy.requireRoomLockedByBatch(batchId, roomId);
        Map<String, Object> room = policy.room(roomId, true);
        if (categoryCount > 1 && !"MIXED".equals(String.valueOf(room.get("resident_scope")))) {
            throw new BusinessException(
                    "TEAM_STUDENT_CATEGORY_MISMATCH",
                    "国内生与国际生混合队伍只能选择混住宿舍",
                    HttpStatus.CONFLICT);
        }
        policy.requireRoomCapacity(roomId, members.size());

        for (Map<String, Object> member : members) {
            long studentId = ((Number) member.get("student_id")).longValue();
            policy.requireBatchEligibility(batchId, studentId);
            Map<String, Object> student = policy.student(studentId);
            policy.requireStudentEligibleForRoom(student, batch, room);
            policy.requireNoActiveResidency(studentId);
        }

        List<Map<String, Object>> residencies = new ArrayList<>();
        for (Map<String, Object> member : members) {
            residencies.add(residencyService.assign(
                    ((Number) member.get("student_id")).longValue(),
                    roomId,
                    null,
                    batchId,
                    teamId,
                    "ROOM",
                    "TEAM_ROOM_SELECT",
                    "队伍整体选择寝室；具体床位由寝室成员自行协商",
                    user));
        }
        jdbc.update("""
                UPDATE selection_team
                SET team_status='COMPLETED', version=version+1,
                    updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=:teamId
                """, Map.of("teamId", teamId));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("selectionMode", "ROOM");
        result.put("teamId", team.get("id"));
        result.put("roomId", roomId);
        result.put("memberCount", members.size());
        result.put("bedAssigned", false);
        result.put("message", "队伍寝室选择成功，具体床位由寝室成员入住后自行协商");
        result.put("residencies", residencies);
        return result;
    }

    private Map<String, Object> requireRoomModeOpen(long batchId) {
        Map<String, Object> batch = policy.batch(batchId);
        if (!"ROOM".equals(String.valueOf(batch.get("selection_mode")))) {
            throw new BusinessException(
                    "BATCH_SELECTION_MODE_MISMATCH",
                    "当前批次为选择床位模式，不能使用选择寝室接口",
                    HttpStatus.CONFLICT);
        }
        if (!"OPEN".equals(String.valueOf(batch.get("batch_status")))) {
            throw new BusinessException(
                    "BATCH_NOT_OPEN",
                    "当前批次尚未开放或已经结束",
                    HttpStatus.CONFLICT);
        }
        return batch;
    }

    private void requireNoActiveTeam(long batchId, long studentId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM selection_team_member
                WHERE batch_id=:batchId AND student_id=:studentId
                  AND member_status IN ('INVITED','JOINED','LOCKED')
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("studentId", studentId), Integer.class);
        if (count != null && count > 0) {
            throw new BusinessException(
                    "TEAM_PERSONAL_SELECTION_REQUIRED",
                    "请先退出或解散当前队伍，再进行个人选寝",
                    HttpStatus.CONFLICT);
        }
    }

    private Map<String, Object> requireLockedLeaderTeam(
            long batchId,
            long teamId,
            long leaderStudentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, batch_id, leader_student_id, team_status
                FROM selection_team
                WHERE id=:teamId AND batch_id=:batchId
                  AND leader_student_id=:leaderStudentId
                  AND team_status='LOCKED'
                FOR UPDATE
                """, new MapSqlParameterSource()
                .addValue("teamId", teamId)
                .addValue("batchId", batchId)
                .addValue("leaderStudentId", leaderStudentId));
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "TEAM_NOT_READY",
                    "只有锁定队伍的队长可以选择寝室",
                    HttpStatus.CONFLICT);
        }
        return rows.getFirst();
    }
}
