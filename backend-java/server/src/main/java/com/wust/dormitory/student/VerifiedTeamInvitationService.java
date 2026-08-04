package com.wust.dormitory.student;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class VerifiedTeamInvitationService {
    private final NamedParameterJdbcTemplate jdbc;
    private final TeamService teamService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public VerifiedTeamInvitationService(
            NamedParameterJdbcTemplate jdbc,
            TeamService teamService,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.teamService = teamService;
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

        Integer matches = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM active_batch_student_lock inviter_lock
                JOIN selection_batch batch_record ON batch_record.id=inviter_lock.batch_id
                JOIN batch_student_eligibility eligibility
                  ON eligibility.batch_id=batch_record.id
                 AND eligibility.eligibility_status='ELIGIBLE'
                JOIN student invitee ON invitee.id=eligibility.student_id
                WHERE inviter_lock.student_id=:inviterId
                  AND batch_record.batch_status IN ('PUBLISHED','OPEN')
                  AND invitee.student_number=:studentNumber
                  AND invitee.student_name=:studentName
                """, new MapSqlParameterSource()
                .addValue("inviterId", user.studentId())
                .addValue("studentNumber", normalizedNumber)
                .addValue("studentName", normalizedName), Integer.class);
        if (matches == null || matches != 1) {
            throw identityMismatch();
        }

        if (teamService.teams(user).isEmpty()) {
            teamService.createFormingTeam(user);
        }
        return teamService.inviteTeammate(normalizedNumber, user);
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
        if (((Number) invitation.get("leader_student_id")).longValue() != user.studentId()) {
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
                SET invitation_status='CANCELLED', responded_at=CURRENT_TIMESTAMP(3)
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

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("JSON_ERROR", "邀请通知序列化失败");
        }
    }
}
