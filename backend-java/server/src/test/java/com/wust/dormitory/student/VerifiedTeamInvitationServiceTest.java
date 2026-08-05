package com.wust.dormitory.student;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerifiedTeamInvitationServiceTest {
    private NamedParameterJdbcTemplate jdbc;
    private TeamService teamService;
    private TeamFormationService formationService;
    private AuditService auditService;
    private VerifiedTeamInvitationService service;
    private CurrentUser user;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        teamService = mock(TeamService.class);
        formationService = mock(TeamFormationService.class);
        auditService = mock(AuditService.class);
        service = new VerifiedTeamInvitationService(
                jdbc,
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
        when(jdbc.queryForList(
                anyString(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class)))
                .thenReturn(List.of());

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
        ArgumentCaptor<String> updateSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).update(
                updateSql.capture(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class));
        assertThat(String.join("\n", updateSql.getAllValues()))
                .contains("ON DUPLICATE KEY UPDATE")
                .contains("invitation_status, invitation_token");
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
        verify(jdbc, never()).update(
                anyString(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class));
    }

    @Test
    void leaderCanCancelPendingInvitationWhileTeamIsForming() {
        when(jdbc.queryForList(
                anyString(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of(
                        "invitation_id", 55L,
                        "invitee_student_id", 101L,
                        "student_number", "202600000011",
                        "student_name", "受邀学生",
                        "team_status", "FORMING",
                        "leader_student_id", 100L)));
        when(jdbc.update(anyString(), anyMap())).thenReturn(1);
        when(jdbc.update(
                anyString(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class)))
                .thenReturn(1);

        Map<String, Object> result = service.cancelInvitation(7L, 101L, user);

        assertThat(result)
                .containsEntry("teamId", 7L)
                .containsEntry("studentId", 101L)
                .containsEntry("cancelled", true);
        verify(jdbc).update(anyString(), anyMap());
        verify(jdbc, times(2)).update(
                anyString(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class));
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
        when(jdbc.queryForObject(
                anyString(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class),
                eq(Integer.class)))
                .thenReturn(1);
        when(jdbc.queryForList(
                anyString(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of(
                        "invitation_id", 55L,
                        "invitee_student_id", 101L,
                        "student_number", "202600000011",
                        "student_name", "受邀学生",
                        "team_status", "FORMING",
                        "leader_student_id", 100L)));
        when(jdbc.update(anyString(), anyMap())).thenReturn(1);
        when(jdbc.update(
                anyString(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class)))
                .thenReturn(1);

        Map<String, Object> result = service.removeOrCancel(7L, 101L, user);

        assertThat(result).containsEntry("cancelled", true);
        verify(teamService, never()).removeMember(anyLong(), anyLong(), eq(user));
    }

    @Test
    void removeOrCancelDelegatesAcceptedMemberToTeamService() {
        when(jdbc.queryForObject(
                anyString(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class),
                eq(Integer.class)))
                .thenReturn(0);
        when(teamService.removeMember(7L, 101L, user)).thenReturn(Map.of(
                "teamId", 7L,
                "studentId", 101L,
                "removed", true));

        Map<String, Object> result = service.removeOrCancel(7L, 101L, user);

        assertThat(result).containsEntry("removed", true);
        verify(teamService).removeMember(7L, 101L, user);
    }

    private void stubSuccessfulInvitation(int duplicatePending) {
        when(jdbc.queryForList(
                anyString(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of(
                        "id", 101L,
                        "student_number", "202600000011",
                        "student_name", "受邀学生",
                        "gender", "F")))
                .thenReturn(List.of(Map.of(
                        "id", 31L,
                        "batch_id", 7L,
                        "team_status", "FORMING",
                        "member_role", "LEADER",
                        "team_max_size", 5)));
        when(jdbc.queryForList(anyString(), anyMap()))
                .thenReturn(List.of(Map.of("gender", "F")));
        when(jdbc.queryForObject(
                anyString(),
                anyMap(),
                eq(Integer.class)))
                .thenReturn(1);
        when(jdbc.queryForObject(
                anyString(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class),
                eq(Integer.class)))
                .thenReturn(0, duplicatePending);
        when(jdbc.update(
                anyString(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class)))
                .thenReturn(1);
    }
}
