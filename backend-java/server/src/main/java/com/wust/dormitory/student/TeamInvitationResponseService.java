package com.wust.dormitory.student;

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
public class TeamInvitationResponseService {
    private final NamedParameterJdbcTemplate jdbc;
    private final TeamFormationService formationService;
    private final AuditService auditService;

    public TeamInvitationResponseService(
            NamedParameterJdbcTemplate jdbc,
            TeamFormationService formationService,
            AuditService auditService) {
        this.jdbc = jdbc;
        this.formationService = formationService;
        this.auditService = auditService;
    }

    @Transactional
    public void respond(String token, boolean accepted, CurrentUser user) {
        List<Map<String, Object>> invitations = jdbc.queryForList("""
                SELECT invitation.id, invitation.team_id,
                       invitation.invitee_student_id,
                       team.batch_id, team.team_status
                FROM team_invitation invitation
                JOIN selection_team team ON team.id=invitation.team_id
                WHERE invitation.invitation_token=:token
                  AND invitation.invitee_student_id=:studentId
                  AND invitation.invitation_status='PENDING'
                  AND invitation.expires_at>CURRENT_TIMESTAMP(3)
                FOR UPDATE
                """, new MapSqlParameterSource()
                .addValue("token", token)
                .addValue("studentId", user.studentId()));
        if (invitations.isEmpty()) {
            throw new BusinessException(
                    "INVITATION_INVALID",
                    "邀请不存在、已处理或已过期",
                    HttpStatus.CONFLICT);
        }

        Map<String, Object> invitation = invitations.getFirst();
        if (!"FORMING".equals(String.valueOf(invitation.get("team_status")))) {
            throw new BusinessException(
                    "INVITATION_INVALID",
                    "小组已经开始选寝，该邀请已失效",
                    HttpStatus.CONFLICT);
        }
        if (accepted) {
            formationService.requireUnassigned(
                    user.studentId(),
                    "你已经确定寝室或床位，不能接受组队邀请");
            Integer joined = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM selection_team_member
                    WHERE batch_id=:batchId
                      AND student_id=:studentId
                      AND member_status IN ('JOINED','LOCKED')
                    """, new MapSqlParameterSource()
                    .addValue("batchId", invitation.get("batch_id"))
                    .addValue("studentId", user.studentId()), Integer.class);
            if (joined != null && joined > 0) {
                throw new BusinessException(
                        "TEAM_ALREADY_JOINED",
                        "你已经加入当前批次的其他队伍",
                        HttpStatus.CONFLICT);
            }
        }

        String invitationStatus = accepted ? "ACCEPTED" : "REJECTED";
        String memberStatus = accepted ? "JOINED" : "REJECTED";
        jdbc.update("""
                UPDATE team_invitation
                SET invitation_status=:status,
                    responded_at=CURRENT_TIMESTAMP(3)
                WHERE id=:invitationId
                  AND invitation_status='PENDING'
                """, new MapSqlParameterSource()
                .addValue("status", invitationStatus)
                .addValue("invitationId", invitation.get("id")));
        jdbc.update("""
                UPDATE selection_team_member
                SET member_status=:status,
                    joined_at=CASE
                        WHEN :status='JOINED' THEN CURRENT_TIMESTAMP(3)
                        ELSE joined_at
                    END,
                    left_at=CASE
                        WHEN :status='REJECTED' THEN CURRENT_TIMESTAMP(3)
                        ELSE NULL
                    END
                WHERE team_id=:teamId
                  AND student_id=:studentId
                  AND member_status='INVITED'
                """, new MapSqlParameterSource()
                .addValue("status", memberStatus)
                .addValue("teamId", invitation.get("team_id"))
                .addValue("studentId", user.studentId()));

        int supersededInvitationCount = 0;
        if (accepted) {
            supersededInvitationCount = jdbc.update("""
                    UPDATE team_invitation other_invitation
                    JOIN selection_team other_team
                      ON other_team.id=other_invitation.team_id
                    SET other_invitation.invitation_status='REJECTED',
                        other_invitation.responded_at=CURRENT_TIMESTAMP(3)
                    WHERE other_team.batch_id=:batchId
                      AND other_invitation.invitee_student_id=:studentId
                      AND other_invitation.id<>:invitationId
                      AND other_invitation.invitation_status='PENDING'
                    """, new MapSqlParameterSource()
                    .addValue("batchId", invitation.get("batch_id"))
                    .addValue("studentId", user.studentId())
                    .addValue("invitationId", invitation.get("id")));
            jdbc.update("""
                    UPDATE selection_team_member other_member
                    JOIN selection_team other_team
                      ON other_team.id=other_member.team_id
                    SET other_member.member_status='REMOVED',
                        other_member.left_at=CURRENT_TIMESTAMP(3)
                    WHERE other_team.batch_id=:batchId
                      AND other_member.student_id=:studentId
                      AND other_member.team_id<>:teamId
                      AND other_member.member_status='INVITED'
                    """, new MapSqlParameterSource()
                    .addValue("batchId", invitation.get("batch_id"))
                    .addValue("studentId", user.studentId())
                    .addValue("teamId", invitation.get("team_id")));
        }

        auditService.success(
                user,
                accepted ? "TEAM_INVITATION_ACCEPT" : "TEAM_INVITATION_REJECT",
                "SELECTION_TEAM",
                invitation.get("team_id"),
                accepted && supersededInvitationCount > 0
                        ? "接受当前邀请并关闭同批次其他待处理邀请"
                        : null,
                null,
                Map.of(
                        "batchId", invitation.get("batch_id"),
                        "supersededInvitationCount", supersededInvitationCount));
    }
}
