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
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TeamService {
    private static final int MAX_TEAM_SIZE = 5;
    private static final Set<String> ACTIVE_TEAM_STATUSES = Set.of("FORMING", "LOCKED", "SELECTING");

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public TeamService(
            NamedParameterJdbcTemplate jdbc,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> inviteTeammate(String studentNumber, CurrentUser user) {
        requireUnassigned(user.studentId(), "你已经确定寝室或床位，不能继续邀请队友");
        Map<String, Object> batch = currentTeamBatch(user.studentId());
        long batchId = number(batch.get("id"));
        Map<String, Object> team = ensureFormingLeaderTeam(batchId, user);
        long teamId = number(team.get("id"));

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

        long inviteeId = number(invitee.get("id"));
        requireUnassigned(inviteeId, "该同学已经确定寝室或床位，不能参与组队");
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
        int configuredMaximum = ((Number) batch.get("team_max_size")).intValue();
        if (occupiedPlaces >= Math.min(configuredMaximum, MAX_TEAM_SIZE)) {
            throw new BusinessException(
                    "TEAM_SIZE_LIMIT",
                    "每个小组最多5人，邀请发起人最多邀请4名队友",
                    HttpStatus.CONFLICT);
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
                        WHERE x.team_id=t.id AND x.member_status IN ('JOINED','LOCKED'))
                            AS confirmed_member_count,
                       (SELECT COUNT(*) FROM team_invitation invitation
                        WHERE invitation.team_id=t.id
                          AND invitation.invitation_status='PENDING'
                          AND invitation.expires_at>CURRENT_TIMESTAMP(3))
                            AS pending_invitation_count
                FROM selection_team_member tm
                JOIN selection_team t ON t.id=tm.team_id
                WHERE tm.student_id=:studentId
                  AND tm.member_status IN ('JOINED','LOCKED')
                  AND t.team_status IN ('FORMING','LOCKED','SELECTING')
                ORDER BY t.created_at DESC
                """, Map.of("studentId", user.studentId()));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> team = new LinkedHashMap<>(row);
            long teamId = number(team.get("id"));
            team.put("member_count", team.get("confirmed_member_count"));
            team.put("members", jdbc.queryForList("""
                    SELECT s.id AS student_id, s.student_number, s.student_name,
                           tm.member_role, tm.member_status
                    FROM selection_team_member tm
                    JOIN student s ON s.id=tm.student_id
                    WHERE tm.team_id=:teamId
                      AND tm.member_status IN ('INVITED','JOINED','LOCKED')
                    ORDER BY tm.member_role='LEADER' DESC, tm.created_at, tm.id
                    """, Map.of("teamId", teamId)));
            result.add(team);
        }
        return result;
    }

    public List<Map<String, Object>> invitations(CurrentUser user) {
        return jdbc.queryForList("""
                SELECT i.invitation_token, i.expires_at, i.team_id, t.batch_id,
                       s.student_name AS inviter_name,
                       s.student_number AS inviter_student_number
                FROM team_invitation i
                JOIN selection_team t ON t.id=i.team_id
                JOIN student s ON s.id=i.inviter_student_id
                WHERE i.invitee_student_id=:studentId
                  AND i.invitation_status='PENDING'
                  AND i.expires_at>CURRENT_TIMESTAMP(3)
                  AND t.team_status='FORMING'
                ORDER BY i.created_at DESC
                """, Map.of("studentId", user.studentId()));
    }

    public List<Map<String, Object>> notifications(CurrentUser user) {
        return jdbc.queryForList("""
                SELECT id, notification_type, title_key, message_key,
                       parameters_json, read_at, created_at
                FROM student_notification
                WHERE student_id=:studentId
                ORDER BY read_at IS NULL DESC, created_at DESC
                LIMIT 50
                """, Map.of("studentId", user.studentId()));
    }

    public void markNotificationRead(long notificationId, CurrentUser user) {
        int updated = jdbc.update("""
                UPDATE student_notification
                SET read_at=COALESCE(read_at, CURRENT_TIMESTAMP(3))
                WHERE id=:notificationId AND student_id=:studentId
                """, new MapSqlParameterSource()
                .addValue("notificationId", notificationId)
                .addValue("studentId", user.studentId()));
        if (updated == 0) {
            throw new BusinessException("NOTIFICATION_NOT_FOUND", "通知不存在", HttpStatus.NOT_FOUND);
        }
    }

    @Transactional
    public void respondInvitation(String token, boolean accepted, CurrentUser user) {
        if (accepted) requireUnassigned(user.studentId(), "你已经确定寝室或床位，不能接受组队邀请");
        Map<String, Object> invitation = one("""
                SELECT invitation.id, invitation.team_id, invitation.invitee_student_id,
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
                .addValue("studentId", user.studentId()),
                "INVITATION_INVALID", "邀请不存在、已处理或已过期");
        if (!"FORMING".equals(invitation.get("team_status"))) {
            throw new BusinessException(
                    "INVITATION_INVALID",
                    "小组已经开始选寝，该邀请已失效",
                    HttpStatus.CONFLICT);
        }

        String invitationStatus = accepted ? "ACCEPTED" : "REJECTED";
        String memberStatus = accepted ? "JOINED" : "REJECTED";
        jdbc.update("""
                UPDATE team_invitation
                SET invitation_status=:status, responded_at=CURRENT_TIMESTAMP(3)
                WHERE id=:id
                """, new MapSqlParameterSource()
                .addValue("status", invitationStatus)
                .addValue("id", invitation.get("id")));
        jdbc.update("""
                UPDATE selection_team_member
                SET member_status=:status,
                    joined_at=CASE WHEN :status='JOINED' THEN CURRENT_TIMESTAMP(3) ELSE joined_at END
                WHERE team_id=:teamId AND student_id=:studentId
                """, new MapSqlParameterSource()
                .addValue("status", memberStatus)
                .addValue("teamId", invitation.get("team_id"))
                .addValue("studentId", user.studentId()));
        auditService.success(
                user,
                accepted ? "TEAM_INVITATION_ACCEPT" : "TEAM_INVITATION_REJECT",
                "SELECTION_TEAM",
                invitation.get("team_id"),
                null,
                null,
                Map.of("batchId", invitation.get("batch_id")));
    }

    @Transactional
    public Map<String, Object> lockTeam(long teamId, CurrentUser user) {
        Map<String, Object> team = leaderTeam(teamId, user.studentId(), Set.of("FORMING"));
        long batchId = number(team.get("batch_id"));
        int confirmedMembers = count("""
                SELECT COUNT(*) FROM selection_team_member
                WHERE team_id=:teamId AND member_status='JOINED'
                """, Map.of("teamId", teamId));
        Map<String, Object> batch = one("""
                SELECT team_min_size, team_max_size
                FROM selection_batch WHERE id=:id
                """, Map.of("id", batchId), "BATCH_NOT_FOUND", "批次不存在");
        int minimum = ((Number) batch.get("team_min_size")).intValue();
        int maximum = Math.min(((Number) batch.get("team_max_size")).intValue(), MAX_TEAM_SIZE);
        if (confirmedMembers < minimum || confirmedMembers > maximum) {
            throw new BusinessException("TEAM_SIZE_INVALID", "已确认成员人数不符合批次规则");
        }

        int invalidatedInvitationCount = cancelPendingInvitations(teamId);
        jdbc.update("""
                UPDATE selection_team
                SET team_status='LOCKED', locked_at=CURRENT_TIMESTAMP(3), version=version+1
                WHERE id=:id
                """, Map.of("id", teamId));
        jdbc.update("""
                UPDATE selection_team_member
                SET member_status='LOCKED'
                WHERE team_id=:id AND member_status='JOINED'
                """, Map.of("id", teamId));
        auditService.success(
                user,
                "TEAM_LOCK",
                "SELECTION_TEAM",
                teamId,
                invalidatedInvitationCount > 0
                        ? "进入选寝并使未确认邀请失效"
                        : "确认已加入成员并进入选寝",
                null,
                Map.of(
                        "batchId", batchId,
                        "confirmedMemberCount", confirmedMembers,
                        "invalidatedInvitationCount", invalidatedInvitationCount));
        return Map.of(
                "teamId", teamId,
                "batchId", batchId,
                "memberCount", confirmedMembers,
                "invalidatedInvitationCount", invalidatedInvitationCount);
    }

    @Transactional
    public Map<String, Object> removeMember(long teamId, long studentId, CurrentUser user) {
        Map<String, Object> team = leaderTeam(teamId, user.studentId(), Set.of("FORMING", "LOCKED"));
        Map<String, Object> member = one("""
                SELECT member.student_id, member.member_role, member.member_status,
                       student.student_name
                FROM selection_team_member member
                JOIN student ON student.id=member.student_id
                WHERE member.team_id=:teamId AND member.student_id=:studentId
                  AND member.member_status IN ('JOINED','LOCKED')
                FOR UPDATE
                """, new MapSqlParameterSource()
                .addValue("teamId", teamId)
                .addValue("studentId", studentId),
                "TEAM_MEMBER_NOT_FOUND", "队友不存在或尚未接受邀请");
        if ("LEADER".equals(member.get("member_role"))) {
            throw new BusinessException("TEAM_MEMBER_REMOVE_LEADER", "邀请发起人不能移除自己");
        }
        long batchId = number(team.get("batch_id"));
        if (count("""
                SELECT COUNT(*) FROM bed_assignment
                WHERE batch_id=:batchId AND student_id=:studentId
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("studentId", studentId)) > 0) {
            throw new BusinessException(
                    "TEAM_MEMBER_ASSIGNED",
                    "该队友已经完成选寝，不能移除",
                    HttpStatus.CONFLICT);
        }

        jdbc.update("""
                UPDATE selection_team_member
                SET member_status='REMOVED', left_at=CURRENT_TIMESTAMP(3)
                WHERE team_id=:teamId AND student_id=:studentId
                """, new MapSqlParameterSource()
                .addValue("teamId", teamId)
                .addValue("studentId", studentId));
        reopenLockedTeam(teamId, String.valueOf(team.get("team_status")));
        createNotification(
                studentId,
                "TEAM_MEMBER_REMOVED",
                "notification.teamRemoved.title",
                "notification.teamRemoved.message",
                Map.of("leaderName", user.displayName(), "teamId", teamId));
        auditService.success(
                user,
                "TEAM_MEMBER_REMOVED",
                "SELECTION_TEAM",
                teamId,
                "邀请发起人移除已接受队友",
                member,
                Map.of("studentId", studentId));
        return Map.of(
                "teamId", teamId,
                "studentId", studentId,
                "studentName", member.get("student_name"),
                "removed", true);
    }

    @Transactional
    public Map<String, Object> leaveTeam(long teamId, CurrentUser user) {
        return leaveMembership(membership(teamId, user.studentId()), user);
    }

    @Transactional
    public Map<String, Object> preparePersonalSelection(long batchId, CurrentUser user) {
        List<Map<String, Object>> memberships = jdbc.queryForList("""
                SELECT team.id, team.batch_id, team.team_status, team.leader_student_id,
                       member.member_role, member.member_status
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
        if (memberships.isEmpty()) {
            return Map.of("leftTeam", false, "batchId", batchId);
        }
        Map<String, Object> result = leaveMembership(memberships.getFirst(), user);
        Map<String, Object> response = new LinkedHashMap<>(result);
        response.put("leftTeam", true);
        response.put("batchId", batchId);
        return response;
    }

    private Map<String, Object> leaveMembership(Map<String, Object> membership, CurrentUser user) {
        long teamId = number(membership.get("id"));
        long batchId = number(membership.get("batch_id"));
        if (count("""
                SELECT COUNT(*) FROM bed_assignment
                WHERE batch_id=:batchId AND student_id=:studentId
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("studentId", user.studentId())) > 0) {
            throw new BusinessException(
                    "TEAM_LEAVE_ASSIGNED",
                    "已经完成选寝，不能退出队伍",
                    HttpStatus.CONFLICT);
        }
        String teamStatus = String.valueOf(membership.get("team_status"));
        if (!ACTIVE_TEAM_STATUSES.contains(teamStatus)) {
            throw new BusinessException(
                    "TEAM_STATUS_INVALID",
                    "当前队伍状态不能退出",
                    HttpStatus.CONFLICT);
        }

        boolean leader = "LEADER".equals(membership.get("member_role"));
        if (leader) {
            List<Long> acceptedMembers = jdbc.query("""
                    SELECT student_id FROM selection_team_member
                    WHERE team_id=:teamId AND member_role='MEMBER'
                      AND member_status IN ('JOINED','LOCKED')
                    """, Map.of("teamId", teamId), (rs, rowNum) -> rs.getLong(1));
            int invalidated = cancelPendingInvitations(teamId);
            jdbc.update("""
                    UPDATE selection_team_member
                    SET member_status='LEFT', left_at=CURRENT_TIMESTAMP(3)
                    WHERE team_id=:teamId AND member_status IN ('JOINED','LOCKED')
                    """, Map.of("teamId", teamId));
            jdbc.update("""
                    UPDATE selection_team
                    SET team_status='DISSOLVED', version=version+1
                    WHERE id=:teamId
                    """, Map.of("teamId", teamId));
            for (Long acceptedMember : acceptedMembers) {
                createNotification(
                        acceptedMember,
                        "TEAM_DISSOLVED",
                        "notification.teamDissolved.title",
                        "notification.teamDissolved.message",
                        Map.of("leaderName", user.displayName(), "teamId", teamId));
            }
            auditService.success(
                    user,
                    "TEAM_DISSOLVED_BY_LEADER",
                    "SELECTION_TEAM",
                    teamId,
                    "邀请发起人退出队伍",
                    null,
                    Map.of("batchId", batchId, "invalidatedInvitationCount", invalidated));
            return Map.of("teamId", teamId, "dissolved", true);
        }

        jdbc.update("""
                UPDATE selection_team_member
                SET member_status='LEFT', left_at=CURRENT_TIMESTAMP(3)
                WHERE team_id=:teamId AND student_id=:studentId
                  AND member_status IN ('JOINED','LOCKED')
                """, new MapSqlParameterSource()
                .addValue("teamId", teamId)
                .addValue("studentId", user.studentId()));
        reopenLockedTeam(teamId, teamStatus);
        auditService.success(
                user,
                "TEAM_LEAVE",
                "SELECTION_TEAM",
                teamId,
                "成员主动退出队伍",
                null,
                Map.of("batchId", batchId));
        return Map.of("teamId", teamId, "dissolved", false);
    }

    private void reopenLockedTeam(long teamId, String currentStatus) {
        if (!"LOCKED".equals(currentStatus) && !"SELECTING".equals(currentStatus)) {
            return;
        }
        jdbc.update("""
                UPDATE selection_team
                SET team_status='FORMING', locked_at=NULL, version=version+1
                WHERE id=:teamId
                """, Map.of("teamId", teamId));
        jdbc.update("""
                UPDATE selection_team_member
                SET member_status='JOINED'
                WHERE team_id=:teamId AND member_status='LOCKED'
                """, Map.of("teamId", teamId));
    }

    private int cancelPendingInvitations(long teamId) {
        List<Long> invitees = jdbc.query("""
                SELECT invitee_student_id FROM team_invitation
                WHERE team_id=:teamId AND invitation_status='PENDING'
                """, Map.of("teamId", teamId), (rs, rowNum) -> rs.getLong(1));
        if (invitees.isEmpty()) {
            return 0;
        }
        jdbc.update("""
                UPDATE team_invitation
                SET invitation_status='CANCELLED', responded_at=CURRENT_TIMESTAMP(3)
                WHERE team_id=:teamId AND invitation_status='PENDING'
                """, Map.of("teamId", teamId));
        jdbc.update("""
                UPDATE selection_team_member
                SET member_status='REMOVED', left_at=CURRENT_TIMESTAMP(3)
                WHERE team_id=:teamId AND member_status='INVITED'
                """, Map.of("teamId", teamId));
        for (Long invitee : invitees) {
            createNotification(
                    invitee,
                    "TEAM_INVITATION_CANCELLED",
                    "notification.invitationCancelled.title",
                    "notification.invitationCancelled.message",
                    Map.of("teamId", teamId));
        }
        return invitees.size();
    }

    private void createNotification(
            long studentId,
            String type,
            String titleKey,
            String messageKey,
            Map<String, Object> parameters) {
        jdbc.update("""
                INSERT INTO student_notification
                (student_id, notification_type, title_key, message_key, parameters_json)
                VALUES (:studentId, :type, :titleKey, :messageKey, CAST(:parameters AS JSON))
                """, new MapSqlParameterSource()
                .addValue("studentId", studentId)
                .addValue("type", type)
                .addValue("titleKey", titleKey)
                .addValue("messageKey", messageKey)
                .addValue("parameters", json(parameters)));
    }

    private Map<String, Object> membership(long teamId, long studentId) {
        return one("""
                SELECT team.id, team.batch_id, team.team_status, team.leader_student_id,
                       member.member_role, member.member_status
                FROM selection_team_member member
                JOIN selection_team team ON team.id=member.team_id
                WHERE team.id=:teamId
                  AND member.student_id=:studentId
                  AND member.member_status IN ('JOINED','LOCKED')
                FOR UPDATE
                """, new MapSqlParameterSource()
                .addValue("teamId", teamId)
                .addValue("studentId", studentId),
                "TEAM_NOT_FOUND", "队伍不存在或你不是有效成员");
    }


    @Transactional
    public Map<String, Object> createFormingTeam(CurrentUser user) {
        requireUnassigned(user.studentId(), "你已经确定寝室或床位，不能创建队伍");
        Map<String, Object> batch = currentTeamBatch(user.studentId());
        long batchId = number(batch.get("id"));
        int memberships = count("""
                SELECT COUNT(*) FROM selection_team_member
                WHERE batch_id=:batchId AND student_id=:studentId AND active_marker=1
                """, new MapSqlParameterSource().addValue("batchId", batchId).addValue("studentId", user.studentId()));
        if (memberships > 0) {
            throw new BusinessException("TEAM_ALREADY_JOINED", "你已经加入当前批次的队伍", HttpStatus.CONFLICT);
        }
        return createInternalTeam(batchId, user);
    }

    private void requireUnassigned(long studentId, String message) {
        int count = count("""
                SELECT (
                    EXISTS(SELECT 1 FROM room_assignment WHERE student_id=:studentId AND assignment_status='ACTIVE')
                    OR EXISTS(SELECT 1 FROM bed_assignment WHERE student_id=:studentId AND assignment_status='ACTIVE')
                )
                """, Map.of("studentId", studentId));
        if (count > 0) throw new BusinessException("TEAM_ASSIGNED_FORBIDDEN", message, HttpStatus.CONFLICT);
    }
    private Map<String, Object> currentTeamBatch(long studentId) {
        return one("""
                SELECT sb.id, sb.team_min_size, sb.team_max_size
                FROM active_batch_student_lock active_lock
                JOIN selection_batch sb ON sb.id=active_lock.batch_id
                JOIN batch_student_eligibility eligibility
                  ON eligibility.batch_id=sb.id
                 AND eligibility.student_id=active_lock.student_id
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
            throw new BusinessException("TEAM_FORMING_REQUIRED", "请先创建处于组队中的队伍", HttpStatus.CONFLICT);
        }
        Map<String, Object> team = memberships.getFirst();
        if (!"LEADER".equals(team.get("member_role"))) {
            throw new BusinessException(
                    "TEAM_INVITE_FORBIDDEN",
                    "你已经作为成员加入小组，只有邀请发起人可以继续邀请",
                    HttpStatus.CONFLICT);
        }
        if (!"FORMING".equals(team.get("team_status"))) {
            throw new BusinessException(
                    "TEAM_STATUS_INVALID",
                    "当前小组已经开始选寝，不能继续邀请");
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

    private Map<String, Object> leaderTeam(
            long teamId,
            long studentId,
            Set<String> allowedStatuses) {
        Map<String, Object> team = one("""
                SELECT * FROM selection_team
                WHERE id=:teamId AND leader_student_id=:studentId
                FOR UPDATE
                """, new MapSqlParameterSource()
                .addValue("teamId", teamId)
                .addValue("studentId", studentId),
                "TEAM_NOT_FOUND", "队伍不存在或你不是邀请发起人");
        if (!allowedStatuses.contains(String.valueOf(team.get("team_status")))) {
            throw new BusinessException(
                    "TEAM_STATUS_INVALID",
                    "当前队伍状态不允许该操作");
        }
        return team;
    }

    private Map<String, Object> student(long studentId) {
        return one("SELECT id, gender FROM student WHERE id=:studentId",
                Map.of("studentId", studentId),
                "STUDENT_NOT_FOUND", "学生档案不存在");
    }

    private long number(Object value) {
        return ((Number) value).longValue();
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

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                    "JSON_ERROR",
                    "通知参数序列化失败",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
