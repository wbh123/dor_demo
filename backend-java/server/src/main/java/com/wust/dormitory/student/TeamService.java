package com.wust.dormitory.student;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TeamService {
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService auditService;

    public TeamService(NamedParameterJdbcTemplate jdbc, AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    @Transactional
    public Map<String, Object> inviteTeammate(String studentNumber, CurrentUser user) {
        Map<String, Object> batch = currentTeamBatch(user.studentId());
        long batchId = ((Number) batch.get("id")).longValue();
        Map<String, Object> team = ensureFormingLeaderTeam(batchId, user);
        long teamId = ((Number) team.get("id")).longValue();

        Map<String, Object> inviter = student(user.studentId());
        Map<String, Object> invitee = one("""
                SELECT s.id, s.student_number, s.student_name, s.gender
                FROM student s
                JOIN batch_student_eligibility e
                  ON e.student_id=s.id AND e.batch_id=:batchId
                WHERE s.student_number=:studentNumber
                  AND e.eligibility_status='ELIGIBLE'
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("studentNumber", studentNumber),
                "INVITEE_NOT_ELIGIBLE", "被邀请学生不存在或没有当前选寝资格");

        long inviteeId = ((Number) invitee.get("id")).longValue();
        if (inviteeId == user.studentId()) {
            throw new BusinessException("TEAM_INVITE_SELF", "不能邀请自己加入小组");
        }
        if (!inviter.get("gender").equals(invitee.get("gender"))) {
            throw new BusinessException("TEAM_GENDER_MISMATCH", "小组成员性别必须一致");
        }

        int occupiedPlaces = count("""
                SELECT COUNT(*) FROM selection_team_member
                WHERE team_id=:teamId AND active_marker=1
                """, Map.of("teamId", teamId));
        int maxSize = ((Number) batch.get("team_max_size")).intValue();
        if (occupiedPlaces >= maxSize) {
            throw new BusinessException("TEAM_SIZE_LIMIT", "小组人数和待处理邀请已经达到上限", HttpStatus.CONFLICT);
        }

        if (count("""
                SELECT COUNT(*) FROM selection_team_member
                WHERE batch_id=:batchId AND student_id=:studentId AND active_marker=1
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("studentId", inviteeId)) > 0) {
            throw new BusinessException(
                    "TEAM_ALREADY_JOINED",
                    "该同学已经加入小组或有待处理邀请",
                    HttpStatus.CONFLICT);
        }

        String token = UUID.randomUUID().toString();
        try {
            jdbc.update("""
                    INSERT INTO selection_team_member
                    (team_id, batch_id, student_id, member_role, member_status)
                    VALUES (:teamId, :batchId, :studentId, 'MEMBER', 'INVITED')
                    """, new MapSqlParameterSource()
                    .addValue("teamId", teamId)
                    .addValue("batchId", batchId)
                    .addValue("studentId", inviteeId));
            jdbc.update("""
                    INSERT INTO team_invitation
                    (team_id, inviter_student_id, invitee_student_id, invitation_status,
                     invitation_token, expires_at)
                    VALUES (:teamId, :inviterId, :inviteeId, 'PENDING', :token,
                            DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 24 HOUR))
                    """, new MapSqlParameterSource()
                    .addValue("teamId", teamId)
                    .addValue("inviterId", user.studentId())
                    .addValue("inviteeId", inviteeId)
                    .addValue("token", token));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(
                    "TEAM_ALREADY_JOINED",
                    "该同学已经加入小组或有待处理邀请",
                    HttpStatus.CONFLICT);
        }

        auditService.success(
                user,
                "TEAM_INVITE",
                "SELECTION_TEAM",
                teamId,
                null,
                null,
                Map.of("inviteeStudentId", inviteeId));
        return Map.of(
                "invited", true,
                "studentNumber", invitee.get("student_number"),
                "studentName", invitee.get("student_name"));
    }

    public List<Map<String, Object>> teams(CurrentUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT t.id, t.batch_id, t.team_status, t.leader_student_id,
                       tm.member_role, tm.member_status,
                       (SELECT COUNT(*) FROM selection_team_member x
                        WHERE x.team_id=t.id
                          AND x.member_status IN ('JOINED','LOCKED')) AS member_count
                FROM selection_team_member tm
                JOIN selection_team t ON t.id=tm.team_id
                WHERE tm.student_id=:studentId
                  AND tm.member_status IN ('JOINED','LOCKED')
                ORDER BY t.created_at DESC
                """, Map.of("studentId", user.studentId()));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> team = new LinkedHashMap<>(row);
            long teamId = ((Number) team.get("id")).longValue();
            List<Map<String, Object>> members = jdbc.queryForList("""
                    SELECT s.student_number, s.student_name,
                           tm.member_role, tm.member_status
                    FROM selection_team_member tm
                    JOIN student s ON s.id=tm.student_id
                    WHERE tm.team_id=:teamId AND tm.active_marker=1
                    ORDER BY tm.member_role='LEADER' DESC, tm.created_at, tm.id
                    """, Map.of("teamId", teamId));
            team.put("members", members);
            result.add(team);
        }
        return result;
    }

    public List<Map<String, Object>> invitations(CurrentUser user) {
        return jdbc.queryForList("""
                SELECT i.invitation_token, i.expires_at,
                       s.student_name AS inviter_name,
                       s.student_number AS inviter_student_number
                FROM team_invitation i
                JOIN student s ON s.id=i.inviter_student_id
                WHERE i.invitee_student_id=:studentId
                  AND i.invitation_status='PENDING'
                  AND i.expires_at>CURRENT_TIMESTAMP(3)
                ORDER BY i.created_at DESC
                """, Map.of("studentId", user.studentId()));
    }

    private Map<String, Object> currentTeamBatch(long studentId) {
        return one("""
                SELECT sb.id, sb.team_min_size, sb.team_max_size
                FROM active_batch_student_lock active_lock
                JOIN selection_batch sb ON sb.id=active_lock.batch_id
                JOIN batch_student_eligibility eligibility
                  ON eligibility.batch_id=sb.id AND eligibility.student_id=active_lock.student_id
                WHERE active_lock.student_id=:studentId
                  AND eligibility.eligibility_status='ELIGIBLE'
                  AND sb.allow_team=1
                  AND sb.batch_status IN ('PUBLISHED','OPEN')
                """, Map.of("studentId", studentId),
                "TEAM_NOT_AVAILABLE", "当前没有可以组队的选寝活动");
    }

    private Map<String, Object> ensureFormingLeaderTeam(long batchId, CurrentUser user) {
        List<Map<String, Object>> memberships = jdbc.queryForList("""
                SELECT t.id, t.batch_id, t.team_status, tm.member_role
                FROM selection_team_member tm
                JOIN selection_team t ON t.id=tm.team_id
                WHERE tm.batch_id=:batchId
                  AND tm.student_id=:studentId
                  AND tm.active_marker=1
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("studentId", user.studentId()));

        if (memberships.isEmpty()) {
            return createInternalTeam(batchId, user);
        }

        Map<String, Object> team = memberships.getFirst();
        if (!"LEADER".equals(team.get("member_role"))) {
            throw new BusinessException(
                    "TEAM_INVITE_FORBIDDEN",
                    "你已经作为成员加入小组，只有邀请发起人可以继续邀请",
                    HttpStatus.CONFLICT);
        }
        if (!"FORMING".equals(team.get("team_status"))) {
            throw new BusinessException("TEAM_STATUS_INVALID", "当前小组已确认成员，不能继续邀请");
        }
        return team;
    }

    private Map<String, Object> createInternalTeam(long batchId, CurrentUser user) {
        String code = "T" + batchId + "-" + UUID.randomUUID().toString()
                .substring(0, 8)
                .toUpperCase();
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO selection_team
                (batch_id, team_code, team_name, leader_student_id, team_status)
                VALUES (:batchId, :code, :internalName, :studentId, 'FORMING')
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("code", code)
                .addValue("internalName", code)
                .addValue("studentId", user.studentId()),
                keyHolder,
                new String[]{"id"});
        long teamId = keyHolder.getKey().longValue();
        jdbc.update("""
                INSERT INTO selection_team_member
                (team_id, batch_id, student_id, member_role, member_status, joined_at)
                VALUES (:teamId, :batchId, :studentId, 'LEADER', 'JOINED', CURRENT_TIMESTAMP(3))
                """, new MapSqlParameterSource()
                .addValue("teamId", teamId)
                .addValue("batchId", batchId)
                .addValue("studentId", user.studentId()));
        auditService.success(
                user,
                "TEAM_CREATE_INTERNAL",
                "SELECTION_TEAM",
                teamId,
                null,
                null,
                Map.of("batchId", batchId));
        return Map.of(
                "id", teamId,
                "batch_id", batchId,
                "team_status", "FORMING",
                "member_role", "LEADER");
    }

    private Map<String, Object> student(long studentId) {
        return one("SELECT id, gender FROM student WHERE id=:studentId",
                Map.of("studentId", studentId),
                "STUDENT_NOT_FOUND", "学生档案不存在");
    }

    private int count(String sql, Map<String, ?> parameters) {
        Integer value = jdbc.queryForObject(sql, parameters, Integer.class);
        return value == null ? 0 : value;
    }

    private int count(String sql, MapSqlParameterSource parameters) {
        Integer value = jdbc.queryForObject(sql, parameters, Integer.class);
        return value == null ? 0 : value;
    }

    private Map<String, Object> one(
            String sql,
            Map<String, ?> parameters,
            String code,
            String message) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, parameters);
        if (rows.isEmpty()) {
            throw new BusinessException(code, message, HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    private Map<String, Object> one(
            String sql,
            MapSqlParameterSource parameters,
            String code,
            String message) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, parameters);
        if (rows.isEmpty()) {
            throw new BusinessException(code, message, HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }
}
