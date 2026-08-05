package com.wust.dormitory.student;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class VerifiedTeamInvitationService {
    private static final int MAX_TEAM_SIZE = 5;

    private final NamedParameterJdbcTemplate jdbc;
    private final TeamService teamService;
    private final TeamFormationService formationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public VerifiedTeamInvitationService(
            NamedParameterJdbcTemplate jdbc,
            TeamService teamService,
            TeamFormationService formationService,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.teamService = teamService;
        this.formationService = formationService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> invite(
            String studentNumber,
            String studentName,
            CurrentUser user) {
        String normalizedNumber = studentNumber == null ? "" : studentNumber.trim();
        String normalizedName = studentName == null ? "" : studentName.trim();
        if (!normalizedNumber.matches("\\d{12}") || normalizedName.isEmpty()) {
            throw identityMismatch();
        }

        formationService.requireUnassigned(
                user.studentId(),
                "你已经确定寝室或床位，不能继续邀请队友");
        long batchId = formationService.currentBatchId(user.studentId());
        List<Map<String, Object>> invitees = jdbc.queryForList("""
                SELECT invitee.id, invitee.student_number, invitee.student_name,
                       invitee.gender
                FROM batch_student_eligibility eligibility
                JOIN student invitee ON invitee.id=eligibility.student_id
                WHERE eligibility.batch_id=:batchId
                  AND eligibility.eligibility_status='ELIGIBLE'
                  AND invitee.student_number=:studentNumber
                  AND invitee.student_name=:studentName
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("studentNumber", normalizedNumber)
                .addValue("studentName", normalizedName));
        if (invitees.size() != 1) {
            throw identityMismatch();
        }

        Map<String, Object> invitee = invitees.getFirst();
        long inviteeId = number(invitee.get("id"));
        if (inviteeId == user.studentId()) {
            throw new BusinessException("TEAM_INVITE_SELF", "不能邀请自己加入小组");
        }
        formationService.requireUnassigned(
                inviteeId,
                "该同学已经确定寝室或床位，不能参与组队");

        List<Map<String, Object>> memberships = jdbc.queryForList("""
                SELECT team.id, team.batch_id, team.team_status,
                       member.member_role
                FROM selection_team_member member
                JOIN selection_team team ON team.id=member.team_id
                WHERE member.batch_id=:batchId
                  AND member.student_id=:studentId
                  AND member.member_status IN ('JOINED','LOCKED')
                  AND team.team_status IN ('FORMING','LOCKED','SELECTING')
                FOR UPDATE
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("studentId", user.studentId()));
        Map<String, Object> team = memberships.isEmpty()
                ? formationService.create(user)
                : memberships.getFirst();
        if (!"LEADER".equals(String.valueOf(team.get("member_role")))) {
            throw new BusinessException(
                    "TEAM_INVITE_FORBIDDEN",
                    "你已经作为成员加入小组，只有邀请发起人可以继续邀请",
                    HttpStatus.CONFLICT);
        }
        if (!"FORMING".equals(String.valueOf(team.get("team_status")))) {
            throw new BusinessException(
                    "TEAM_STATUS_INVALID",
                    "当前小组已经开始选寝，不能继续邀请",
                    HttpStatus.CONFLICT);
        }
        long teamId = number(team.get("id"));

        List<Map<String, Object>> inviters = jdbc.queryForList(
                "SELECT gender FROM student WHERE id=:studentId",
                Map.of("studentId", user.studentId()));
        if (inviters.isEmpty()) {
            throw new BusinessException(
                    "STUDENT_NOT_FOUND",
                    "邀请发起人档案不存在",
                    HttpStatus.NOT_FOUND);
        }
        if (!String.valueOf(inviters.getFirst().get("gender"))
                .equals(String.valueOf(invitee.get("gender")))) {
            throw new BusinessException(
                    "TEAM_GENDER_MISMATCH",
                    "小组成员性别必须一致",
                    HttpStatus.CONFLICT);
        }

        Integer occupied = jdbc.queryForObject("""
                SELECT (
                    (SELECT COUNT(*) FROM selection_team_member member
                     WHERE member.team_id=:teamId
                       AND member.member_status IN ('JOINED','LOCKED'))
                    +
                    (SELECT COUNT(*) FROM team_invitation invitation
                     WHERE invitation.team_id=:teamId
                       AND invitation.invitation_status='PENDING'
                       AND invitation.expires_at>CURRENT_TIMESTAMP(3))
                )
                """, Map.of("teamId", teamId), Integer.class);
        int configuredMaximum = ((Number) team.getOrDefault("team_max_size", MAX_TEAM_SIZE))
                .intValue();
        if (occupied != null
                && occupied >= Math.min(configuredMaximum, MAX_TEAM_SIZE)) {
            throw new BusinessException(
                    "TEAM_SIZE_LIMIT",
                    "每个小组最多5人，邀请发起人最多邀请4名队友",
                    HttpStatus.CONFLICT);
        }

        Integer joinedElsewhere = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM selection_team_member
                WHERE batch_id=:batchId
                  AND student_id=:studentId
                  AND member_status IN ('JOINED','LOCKED')
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("studentId", inviteeId), Integer.class);
        if (joinedElsewhere != null && joinedElsewhere > 0) {
            throw new BusinessException(
                    "TEAM_ALREADY_JOINED",
                    "该同学已经加入当前批次的其他队伍",
                    HttpStatus.CONFLICT);
        }

        Integer duplicatePending = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM team_invitation
                WHERE team_id=:teamId
                  AND invitee_student_id=:studentId
                  AND invitation_status='PENDING'
                  AND expires_at>CURRENT_TIMESTAMP(3)
                """, new MapSqlParameterSource()
                .addValue("teamId", teamId)
                .addValue("studentId", inviteeId), Integer.class);
        if (duplicatePending != null && duplicatePending > 0) {
            throw new BusinessException(
                    "TEAM_INVITATION_PENDING_DUPLICATE",
                    "当前队伍已经向该同学发送待处理邀请",
                    HttpStatus.CONFLICT);
        }

        String token = UUID.randomUUID().toString();
        try {
            jdbc.update("""
                    INSERT INTO selection_team_member
                    (team_id, batch_id, student_id, member_role, member_status)
                    VALUES (:teamId, :batchId, :studentId, 'MEMBER', 'INVITED')
                    ON DUPLICATE KEY UPDATE
                        member_role='MEMBER',
                        member_status='INVITED',
                        joined_at=NULL,
                        left_at=NULL
                    """, new MapSqlParameterSource()
                    .addValue("teamId", teamId)
                    .addValue("batchId", batchId)
                    .addValue("studentId", inviteeId));
            jdbc.update("""
                    INSERT INTO team_invitation
                    (team_id, inviter_student_id, invitee_student_id,
                     invitation_status, invitation_token, expires_at)
                    VALUES (:teamId, :inviterId, :inviteeId, 'PENDING', :token,
                            DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 24 HOUR))
                    """, new MapSqlParameterSource()
                    .addValue("teamId", teamId)
                    .addValue("inviterId", user.studentId())
                    .addValue("inviteeId", inviteeId)
                    .addValue("token", token));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(
                    "TEAM_INVITATION_CONFLICT",
                    "邀请状态发生变化，请刷新后重试",
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

    @Transactional
    public Map<String, Object> removeOrCancel(
            long teamId,
            long studentId,
            CurrentUser user) {
        Integer pending = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM team_invitation invitation
                JOIN selection_team team ON team.id=invitation.team_id
                WHERE invitation.team_id=:teamId
                  AND invitation.invitee_student_id=:studentId
                  AND invitation.invitation_status='PENDING'
                  AND invitation.expires_at>CURRENT_TIMESTAMP(3)
                  AND team.team_status='FORMING'
                """, new MapSqlParameterSource()
                .addValue("teamId", teamId)
                .addValue("studentId", studentId), Integer.class);
        if (pending != null && pending > 0) {
            return cancelInvitation(teamId, studentId, user);
        }
        return teamService.removeMember(teamId, studentId, user);
    }

    @Transactional
    public Map<String, Object> cancelInvitation(
            long teamId,
            long inviteeStudentId,
            CurrentUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT invitation.id AS invitation_id,
                       invitation.invitee_student_id,
                       student.student_number, student.student_name,
                       team.team_status, team.leader_student_id
                FROM team_invitation invitation
                JOIN selection_team team ON team.id=invitation.team_id
                JOIN student ON student.id=invitation.invitee_student_id
                WHERE invitation.team_id=:teamId
                  AND invitation.invitee_student_id=:inviteeStudentId
                  AND invitation.invitation_status='PENDING'
                  AND invitation.expires_at>CURRENT_TIMESTAMP(3)
                FOR UPDATE
                """, new MapSqlParameterSource()
                .addValue("teamId", teamId)
                .addValue("inviteeStudentId", inviteeStudentId));
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "TEAM_INVITATION_NOT_PENDING",
                    "邀请不存在、已处理或已过期",
                    HttpStatus.CONFLICT);
        }
        Map<String, Object> invitation = rows.getFirst();
        if (((Number) invitation.get("leader_student_id")).longValue()
                != user.studentId()) {
            throw new BusinessException(
                    "TEAM_INVITATION_CANCEL_FORBIDDEN",
                    "只有邀请发起人可以取消邀请",
                    HttpStatus.FORBIDDEN);
        }
        if (!"FORMING".equals(String.valueOf(invitation.get("team_status")))) {
            throw new BusinessException(
                    "TEAM_STATUS_INVALID",
                    "队伍已经开始选寝，不能取消邀请",
                    HttpStatus.CONFLICT);
        }

        jdbc.update("""
                UPDATE team_invitation
                SET invitation_status='CANCELLED',
                    responded_at=CURRENT_TIMESTAMP(3)
                WHERE id=:invitationId
                """, Map.of("invitationId", invitation.get("invitation_id")));
        jdbc.update("""
                UPDATE selection_team_member
                SET member_status='REMOVED', left_at=CURRENT_TIMESTAMP(3)
                WHERE team_id=:teamId AND student_id=:studentId
                  AND member_status='INVITED'
                """, new MapSqlParameterSource()
                .addValue("teamId", teamId)
                .addValue("studentId", inviteeStudentId));
        createNotification(inviteeStudentId, teamId, user.displayName());
        auditService.success(
                user,
                "TEAM_INVITATION_CANCELLED",
                "SELECTION_TEAM",
                teamId,
                "邀请发起人取消待确认邀请",
                invitation,
                Map.of("inviteeStudentId", inviteeStudentId));
        return Map.of(
                "teamId", teamId,
                "studentId", inviteeStudentId,
                "studentNumber", invitation.get("student_number"),
                "studentName", invitation.get("student_name"),
                "cancelled", true);
    }

    private void createNotification(long studentId, long teamId, String leaderName) {
        jdbc.update("""
                INSERT INTO student_notification
                (student_id, notification_type, title_key, message_key, parameters_json)
                VALUES (:studentId, 'TEAM_INVITATION_CANCELLED',
                        'notification.invitationWithdrawn.title',
                        'notification.invitationWithdrawn.message',
                        CAST(:parameters AS JSON))
                """, new MapSqlParameterSource()
                .addValue("studentId", studentId)
                .addValue("parameters", json(Map.of(
                        "teamId", teamId,
                        "leaderName", leaderName))));
    }

    private BusinessException identityMismatch() {
        return new BusinessException(
                "INVITEE_IDENTITY_MISMATCH",
                "学号和姓名未匹配到当前批次可邀请学生，请核对后重试",
                HttpStatus.BAD_REQUEST);
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("JSON_ERROR", "邀请通知序列化失败");
        }
    }
}
