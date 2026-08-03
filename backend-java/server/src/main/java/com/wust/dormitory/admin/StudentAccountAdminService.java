package com.wust.dormitory.admin;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.residency.ResidencyService;
import com.wust.dormitory.roomchange.RoomChangeService;
import com.wust.dormitory.security.AuthTokenService;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.selection.BedHoldResetService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudentAccountAdminService {
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService auditService;
    private final AuthTokenService authTokenService;
    private final BedHoldResetService bedHoldResetService;
    private final ResidencyService residencyService;
    private final RoomChangeService roomChangeService;

    public StudentAccountAdminService(
            NamedParameterJdbcTemplate jdbc,
            AuditService auditService,
            AuthTokenService authTokenService,
            BedHoldResetService bedHoldResetService,
            ResidencyService residencyService,
            RoomChangeService roomChangeService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
        this.authTokenService = authTokenService;
        this.bedHoldResetService = bedHoldResetService;
        this.residencyService = residencyService;
        this.roomChangeService = roomChangeService;
    }

    @Transactional
    public Map<String, Object> resetPassword(
            long studentId,
            String reason,
            CurrentUser operator) {
        validateReason(reason);
        Map<String, Object> before = studentAccount(studentId);
        long userId = number(before.get("user_id"));
        resetAccount(userId);
        int revokedTokens = authTokenService.revokeUser(userId);
        Map<String, Object> after = studentAccount(studentId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("studentId", studentId);
        result.put("studentNumber", after.get("student_number"));
        result.put("accountStatus", after.get("account_status"));
        result.put("revokedTokens", revokedTokens);
        result.put("message", "学生密码已重置，需使用学号和姓名重新激活账号");
        auditService.success(
                operator,
                "STUDENT_PASSWORD_RESET",
                "STUDENT",
                studentId,
                reason.trim(),
                before,
                result);
        return result;
    }

    @Transactional
    public Map<String, Object> resetState(
            long studentId,
            String confirmStudentNumber,
            String reason,
            CurrentUser operator) {
        validateReason(reason);
        Map<String, Object> before = studentAccount(studentId);
        String studentNumber = String.valueOf(before.get("student_number"));
        String studentName = String.valueOf(before.get("student_name"));
        if (!studentNumber.equals(confirmStudentNumber)) {
            throw new BusinessException(
                    "STUDENT_RESET_CONFIRMATION_MISMATCH",
                    "确认学号与当前学生不一致",
                    HttpStatus.CONFLICT);
        }
        long userId = number(before.get("user_id"));
        Map<String, Integer> deleted = new LinkedHashMap<>();

        List<Long> activeTeamIds = activeTeamIds(studentId);
        deleted.put("releasedRedisHolds", bedHoldResetService.releaseAllForStudent(studentId, activeTeamIds));
        deleted.put("cancelledRoomChanges", roomChangeService.cancelActiveRoomChanges(
                studentId,
                "管理员完全重置学生：" + reason.trim(),
                operator));

        List<Long> activeResidencyIds = jdbc.queryForList("""
                SELECT id
                FROM room_assignment
                WHERE student_id=:studentId AND assignment_status='ACTIVE'
                FOR UPDATE
                """, Map.of("studentId", studentId), Long.class);
        for (Long residencyId : activeResidencyIds) {
            residencyService.end(
                    residencyId,
                    "管理员完全重置学生：" + reason.trim(),
                    operator);
        }
        deleted.put("endedResidencies", activeResidencyIds.size());

        deleted.put("allocationResults", delete(
                "DELETE FROM allocation_run_result WHERE student_id=:studentId",
                studentId));
        deleted.put("assignmentHistory", delete(
                "DELETE FROM assignment_history WHERE student_id=:studentId",
                studentId));
        deleted.put("assignments", delete(
                "DELETE FROM bed_assignment WHERE student_id=:studentId",
                studentId));
        deleted.put("questionnaireAnswers", delete(
                "DELETE FROM questionnaire_answer WHERE student_id=:studentId",
                studentId));
        deleted.put("studentFeatures", delete(
                "DELETE FROM student_feature WHERE student_id=:studentId",
                studentId));

        int generatedNotifications = 0;
        int cancelledInvitations = 0;
        int removedTeamMembers = 0;
        int dissolvedTeams = 0;
        for (Long teamId : activeTeamIds) {
            generatedNotifications += jdbc.update("""
                    INSERT INTO student_notification
                    (student_id, notification_type, title_key, message_key,
                     parameters_json, read_at)
                    SELECT member.student_id,
                           'TEAM_DISSOLVED',
                           'notification.teamDissolved.title',
                           'notification.teamDissolved.message',
                           JSON_OBJECT('leaderName', :studentName),
                           NULL
                    FROM selection_team_member member
                    WHERE member.team_id=:teamId
                      AND member.student_id<>:studentId
                      AND member.member_status IN ('INVITED','JOINED','LOCKED')
                    """, Map.of(
                    "teamId", teamId,
                    "studentId", studentId,
                    "studentName", studentName));
            cancelledInvitations += jdbc.update("""
                    UPDATE team_invitation
                    SET invitation_status='CANCELLED',
                        responded_at=COALESCE(responded_at, CURRENT_TIMESTAMP(3))
                    WHERE team_id=:teamId
                      AND invitation_status='PENDING'
                    """, Map.of("teamId", teamId));
            removedTeamMembers += jdbc.update("""
                    UPDATE selection_team_member
                    SET member_status=CASE
                            WHEN student_id=:studentId THEN 'LEFT'
                            ELSE 'REMOVED'
                        END,
                        left_at=CURRENT_TIMESTAMP(3)
                    WHERE team_id=:teamId
                      AND member_status IN ('INVITED','JOINED','LOCKED')
                    """, Map.of("teamId", teamId, "studentId", studentId));
            dissolvedTeams += jdbc.update("""
                    UPDATE selection_team
                    SET team_status='DISSOLVED', version=version+1
                    WHERE id=:teamId
                      AND team_status IN ('FORMING','LOCKED','SELECTING')
                    """, Map.of("teamId", teamId));
        }
        deleted.put("generatedTeamNotifications", generatedNotifications);
        deleted.put("cancelledTeamInvitations", cancelledInvitations);
        deleted.put("removedTeamMembers", removedTeamMembers);
        deleted.put("dissolvedTeams", dissolvedTeams);

        deleted.put("teamInvitations", jdbc.update("""
                DELETE FROM team_invitation
                WHERE inviter_student_id=:studentId OR invitee_student_id=:studentId
                """, Map.of("studentId", studentId)));
        deleted.put("teamMemberships", delete(
                "DELETE FROM selection_team_member WHERE student_id=:studentId",
                studentId));
        deleted.put("notifications", delete(
                "DELETE FROM student_notification WHERE student_id=:studentId",
                studentId));
        deleted.put("activeBatchLocks", delete(
                "DELETE FROM active_batch_student_lock WHERE student_id=:studentId",
                studentId));
        deleted.put("batchEligibilities", delete(
                "DELETE FROM batch_student_eligibility WHERE student_id=:studentId",
                studentId));

        resetAccount(userId);
        int revokedTokens = authTokenService.revokeUser(userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("studentId", studentId);
        result.put("studentNumber", studentNumber);
        result.put("accountStatus", "PENDING");
        result.put("deleted", deleted);
        result.put("revokedTokens", revokedTokens);
        result.put("message", "学生账号、长期在住、临时占用与全部选寝状态已重置为待激活");
        auditService.success(
                operator,
                "STUDENT_STATE_RESET",
                "STUDENT",
                studentId,
                reason.trim(),
                before,
                result);
        return result;
    }

    private List<Long> activeTeamIds(long studentId) {
        return jdbc.queryForList("""
                SELECT DISTINCT team.id
                FROM selection_team team
                LEFT JOIN selection_team_member member
                  ON member.team_id=team.id
                 AND member.student_id=:studentId
                 AND member.member_status IN ('INVITED','JOINED','LOCKED')
                WHERE team.team_status IN ('FORMING','LOCKED','SELECTING')
                  AND (
                    team.leader_student_id=:studentId
                    OR member.student_id IS NOT NULL
                  )
                FOR UPDATE
                """, Map.of("studentId", studentId), Long.class);
    }

    private void resetAccount(long userId) {
        jdbc.update("""
                UPDATE app_user
                SET password_hash=NULL,
                    account_status='PENDING',
                    last_login_at=NULL,
                    welcome_acknowledged_at=NULL,
                    version=version+1
                WHERE id=:userId AND user_type='STUDENT'
                """, Map.of("userId", userId));
    }

    private int delete(String sql, long studentId) {
        return jdbc.update(sql, Map.of("studentId", studentId));
    }

    private void validateReason(String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw new BusinessException(
                    "STUDENT_RESET_REASON_REQUIRED",
                    "请填写1至500个字符的重置原因");
        }
    }

    private Map<String, Object> studentAccount(long studentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT s.id AS student_id,
                       s.student_number,
                       s.student_name,
                       u.id AS user_id,
                       u.account_status,
                       u.last_login_at,
                       u.welcome_acknowledged_at
                FROM student s
                JOIN app_user u ON u.student_id=s.id AND u.user_type='STUDENT'
                WHERE s.id=:studentId
                """, Map.of("studentId", studentId));
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "STUDENT_NOT_FOUND",
                    "学生或学生账号不存在",
                    HttpStatus.NOT_FOUND);
        }
        return new LinkedHashMap<>(rows.getFirst());
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }
}
