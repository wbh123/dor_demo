package com.wust.dormitory.student;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.student.mapper.VerifiedTeamInvitationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerifiedTeamInvitationServiceTest {
    private VerifiedTeamInvitationMapper mapper;
    private TeamService teamService;
    private TeamFormationService formationService;
    private AuditService auditService;
    private VerifiedTeamInvitationService service;
    private CurrentUser user;

    @BeforeEach
    void setUp() {
        mapper = mock(VerifiedTeamInvitationMapper.class);
        teamService = mock(TeamService.class);
        formationService = mock(TeamFormationService.class);
        auditService = mock(AuditService.class);
        service = new VerifiedTeamInvitationService(
                mapper,
                teamService,
                formationService,
                auditService,
                new ObjectMapper());
        user = new CurrentUser(
                10L,
                100L,
                "202600000010",
                "发起学生",
                "STUDENT");
        when(formationService.currentBatchId(user.studentId())).thenReturn(7L);
    }

    @Test
    void rejectsNumberAndNameThatDoNotMatchSameEligibleStudent() {
        when(mapper.findEligibleInvitee(7L, "202600000011", "错误姓名"))
                .thenReturn(null);

        assertThatThrownBy(() -> service.invite(
                "202600000011",
                "错误姓名",
                user))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode())
                                .isEqualTo("INVITEE_IDENTITY_MISMATCH"));
    }

    @Test
    void verifiedInvitationAllowsOtherTeamsPendingInvitation() {
        stubSuccessfulInvitation(0);

        Map<String, Object> result = service.invite(
                "202600000011",
                "受邀学生",
                user);

        assertThat(result)
                .containsEntry("invited", true)
                .containsEntry("studentNumber", "202600000011");
        verify(mapper).upsertInvitedMember(31L, 7L, 101L);
        verify(mapper).insertInvitation(eq(31L), eq(100L), eq(101L), anyString());
        verify(teamService, never()).inviteTeammate(anyString(), eq(user));
    }

    @Test
    void duplicatePendingInvitationFromSameTeamIsRejected() {
        stubSuccessfulInvitation(1);

        assertThatThrownBy(() -> service.invite(
                "202600000011",
                "受邀学生",
                user))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode())
                                .isEqualTo("TEAM_INVITATION_PENDING_DUPLICATE"));
        verify(mapper, never()).upsertInvitedMember(anyLong(), anyLong(), anyLong());
        verify(mapper, never()).insertInvitation(anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    void leaderCanCancelPendingInvitationWhileTeamIsForming() {
        when(mapper.findPendingInvitationForUpdate(7L, 101L))
                .thenReturn(pendingInvitation());

        Map<String, Object> result = service.cancelInvitation(7L, 101L, user);

        assertThat(result)
                .containsEntry("teamId", 7L)
                .containsEntry("studentId", 101L)
                .containsEntry("cancelled", true);
        verify(mapper).cancelInvitation(55L);
        verify(mapper).removeInvitedMember(7L, 101L);
        verify(mapper).insertCancellationNotification(eq(101L), anyString());
        verify(auditService).success(
                eq(user),
                eq("TEAM_INVITATION_CANCELLED"),
                eq("SELECTION_TEAM"),
                eq(7L),
                anyString(),
                anyMap(),
                anyMap());
    }

    @Test
    void removeOrCancelUsesCancellationFlowForPendingInvite() {
        when(mapper.hasPendingInvitation(7L, 101L)).thenReturn(1);
        when(mapper.findPendingInvitationForUpdate(7L, 101L))
                .thenReturn(pendingInvitation());

        Map<String, Object> result = service.removeOrCancel(7L, 101L, user);

        assertThat(result).containsEntry("cancelled", true);
        verify(teamService, never()).removeMember(anyLong(), anyLong(), eq(user));
    }

    @Test
    void removeOrCancelDelegatesAcceptedMemberToTeamService() {
        when(mapper.hasPendingInvitation(7L, 101L)).thenReturn(0);
        when(teamService.removeMember(7L, 101L, user)).thenReturn(Map.of(
                "teamId", 7L,
                "studentId", 101L,
                "removed", true));

        Map<String, Object> result = service.removeOrCancel(7L, 101L, user);

        assertThat(result).containsEntry("removed", true);
        verify(teamService).removeMember(7L, 101L, user);
    }

    private void stubSuccessfulInvitation(int duplicatePending) {
        when(mapper.findEligibleInvitee(7L, "202600000011", "受邀学生"))
                .thenReturn(Map.of(
                        "id", 101L,
                        "student_number", "202600000011",
                        "student_name", "受邀学生",
                        "gender", "F"));
        when(mapper.findLeaderTeamForUpdate(7L, 100L))
                .thenReturn(Map.of(
                        "id", 31L,
                        "batch_id", 7L,
                        "team_status", "FORMING",
                        "member_role", "LEADER",
                        "team_max_size", 5,
                        "inviter_gender", "F"));
        when(mapper.findInvitationGuards(31L, 7L, 101L))
                .thenReturn(Map.of(
                        "occupied_count", 1,
                        "joined_elsewhere", 0,
                        "duplicate_pending", duplicatePending));
        when(mapper.upsertInvitedMember(31L, 7L, 101L)).thenReturn(1);
        when(mapper.insertInvitation(eq(31L), eq(100L), eq(101L), anyString())).thenReturn(1);
    }

    private Map<String, Object> pendingInvitation() {
        return Map.of(
                "invitation_id", 55L,
                "invitee_student_id", 101L,
                "student_number", "202600000011",
                "student_name", "受邀学生",
                "team_status", "FORMING",
                "leader_student_id", 100L);
    }
}
