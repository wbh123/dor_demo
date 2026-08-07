package com.wust.dormitory.student;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.student.mapper.VerifiedTeamInvitationMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class VerifiedTeamInvitationService {
    private static final int MAX_TEAM_SIZE = 5;

    private final VerifiedTeamInvitationMapper mapper;
    private final TeamService teamService;
    private final TeamFormationService formationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public VerifiedTeamInvitationService(
            VerifiedTeamInvitationMapper mapper,
            TeamService teamService,
            TeamFormationService formationService,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this.mapper = mapper;
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
        Map<String, Object> invitee = mapper.findEligibleInvitee(
                batchId, normalizedNumber, normalizedName);
        if (invitee == null || invitee.isEmpty()) {
            throw identityMismatch();
        }

        long inviteeId = number(invitee.get("id"));
        if (inviteeId == user.studentId()) {
            throw new BusinessException("TEAM_INVITE_SELF", "不能邀请自己加入小组");
        }
        formationService.requireUnassigned(
                inviteeId,
                "该同学已经确定寝室或床位，不能参与组队");

        Map<String, Object> team = mapper.findLeaderTeamForUpdate(batchId, user.studentId());
        if (team == null || team.isEmpty()) {
            formationService.create(user);
            team = mapper.findLeaderTeamForUpdate(batchId, user.studentId());
        }
        if (team == null || team.isEmpty()) {
            throw new IllegalStateException("小组创建成功但无法读取队伍上下文");
        }
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
        if (!String.valueOf(team.get("inviter_gender"))
                .equals(String.valueOf(invitee.get("gender")))) {
            throw new BusinessException(
                    "TEAM_GENDER_MISMATCH",
                    "小组成员性别必须一致",
                    HttpStatus.CONFLICT);
        }

        long teamId = number(team.get("id"));
        Map<String, Object> guards = mapper.findInvitationGuards(teamId, batchId, inviteeId);
        int occupied = integer(guards, "occupied_count");
        int configuredMaximum = ((Number) team.get("team_max_size")).intValue();
        if (occupied >= Math.min(configuredMaximum, MAX_TEAM_SIZE)) {
            throw new BusinessException(
                    "TEAM_SIZE_LIMIT",
                    "当前队伍人数和待处理邀请已经达到批次上限",
                    HttpStatus.CONFLICT);
        }
        if (integer(guards, "joined_elsewhere") > 0) {
            throw new BusinessException(
                    "TEAM_ALREADY_JOINED",
                    "该同学已经加入当前批次的其他队伍",
                    HttpStatus.CONFLICT);
        }
        if (integer(guards, "duplicate_pending") > 0) {
            throw new BusinessException(
                    "TEAM_INVITATION_PENDING_DUPLICATE",
                    "当前队伍已经向该同学发送待处理邀请",
                    HttpStatus.CONFLICT);
        }

        String token = UUID.randomUUID().toString();
        try {
            mapper.upsertInvitedMember(teamId, batchId, inviteeId);
            mapper.insertInvitation(teamId, user.studentId(), inviteeId, token);
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
        if (mapper.hasPendingInvitation(teamId, studentId) > 0) {
            return cancelInvitation(teamId, studentId, user);
        }
        return teamService.removeMember(teamId, studentId, user);
    }

    @Transactional
    public Map<String, Object> cancelInvitation(
            long teamId,
            long inviteeStudentId,
            CurrentUser user) {
        Map<String, Object> invitation = mapper.findPendingInvitationForUpdate(
                teamId, inviteeStudentId);
        if (invitation == null || invitation.isEmpty()) {
            throw new BusinessException(
                    "TEAM_INVITATION_NOT_PENDING",
                    "邀请不存在、已处理或已过期",
                    HttpStatus.CONFLICT);
        }
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

        mapper.cancelInvitation(number(invitation.get("invitation_id")));
        mapper.removeInvitedMember(teamId, inviteeStudentId);
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
        mapper.insertCancellationNotification(studentId, json(Map.of(
                "teamId", teamId,
                "leaderName", leaderName)));
    }

    private BusinessException identityMismatch() {
        return new BusinessException(
                "INVITEE_IDENTITY_MISMATCH",
                "学号和姓名未匹配到当前批次可邀请学生，请核对后重试",
                HttpStatus.BAD_REQUEST);
    }

    private int integer(Map<String, Object> row, String key) {
        Object value = row == null ? null : row.get(key);
        return value == null ? 0 : ((Number) value).intValue();
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
