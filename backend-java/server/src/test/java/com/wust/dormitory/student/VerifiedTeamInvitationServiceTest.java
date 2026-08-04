package com.wust.dormitory.student;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerifiedTeamInvitationServiceTest {
    private NamedParameterJdbcTemplate jdbc;
    private TeamService teamService;
    private AuditService auditService;
    private VerifiedTeamInvitationService service;
    private CurrentUser user;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        teamService = mock(TeamService.class);
        auditService = mock(AuditService.class);
        service = new VerifiedTeamInvitationService(
                jdbc,
                teamService,
                auditService,
                new ObjectMapper());
        user = new CurrentUser(10L, 100L, "202600000010", "发起学生", "STUDENT");
    }

    @Test
    void rejectsNumberAndNameThatDoNotMatchSameEligibleStudent() {
        when(jdbc.queryForObject(
                anyString(),
                any(MapSqlParameterSource.class),
                eq(Integer.class)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.invite(
                "202600000011",
                "错误姓名",
                user))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("INVITEE_IDENTITY_MISMATCH"));
    }

    @Test
    void verifiedInvitationDelegatesTeamCreationToInviteTeammate() {
        when(jdbc.queryForObject(
                anyString(),
                any(MapSqlParameterSource.class),
                eq(Integer.class)))
                .thenReturn(1);
        when(teamService.inviteTeammate("202600000011", user)).thenReturn(Map.of(
                "invited", true,
                "studentNumber", "202600000011",
                "studentName", "受邀学生"));

        Map<String, Object> result = service.invite(
                "202600000011",
                "受邀学生",
                user);

        assertThat(result).containsEntry("invited", true);
        verify(teamService).inviteTeammate("202600000011", user);
    }

    @Test
    void leaderCanCancelPendingInvitationWhileTeamIsForming() {
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of(
                        "invitation_id", 55L,
                        "invitee_student_id", 101L,
                        "student_number", "202600000011",
                        "student_name", "受邀学生",
                        "team_status", "FORMING",
                        "leader_student_id", 100L)));
        when(jdbc.update(anyString(), anyMap())).thenReturn(1);
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        Map<String, Object> result = service.cancelInvitation(7L, 101L, user);

        assertThat(result)
                .containsEntry("teamId", 7L)
                .containsEntry("studentId", 101L)
                .containsEntry("cancelled", true);
        verify(jdbc).update(anyString(), anyMap());
        verify(jdbc, times(2)).update(anyString(), any(MapSqlParameterSource.class));
        verify(auditService).success(
                eq(user),
                eq("TEAM_INVITATION_CANCELLED"),
                eq("SELECTION_TEAM"),
                eq(7L),
                anyString(),
                anyMap(),
                anyMap());
    }
}
