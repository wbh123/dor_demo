package com.wust.dormitory.student;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamInvitationResponseServiceTest {
    private NamedParameterJdbcTemplate jdbc;
    private TeamFormationService formationService;
    private AuditService auditService;
    private TeamInvitationResponseService service;
    private CurrentUser invitee;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        formationService = mock(TeamFormationService.class);
        auditService = mock(AuditService.class);
        service = new TeamInvitationResponseService(
                jdbc,
                formationService,
                auditService);
        invitee = new CurrentUser(
                22L,
                202L,
                "202600000202",
                "受邀学生",
                "STUDENT");
    }

    @Test
    void acceptingOneInvitationRejectsOtherPendingInvitations() {
        when(jdbc.queryForList(
                anyString(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of(
                        "id", 55L,
                        "team_id", 31L,
                        "invitee_student_id", 202L,
                        "batch_id", 7L,
                        "team_status", "FORMING")));
        when(jdbc.queryForObject(
                anyString(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class),
                eq(Integer.class)))
                .thenReturn(0);
        when(jdbc.update(
                anyString(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class)))
                .thenReturn(1, 1, 2, 2);

        service.respond("token", true, invitee);

        ArgumentCaptor<String> updateSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, org.mockito.Mockito.times(4)).update(
                updateSql.capture(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class));
        assertThat(String.join("\n", updateSql.getAllValues()))
                .contains("other_invitation.id<>:invitationId")
                .contains("other_member.team_id<>:teamId")
                .contains("other_member.member_status='INVITED'");
        verify(formationService).requireUnassigned(
                invitee.studentId(),
                "你已经确定寝室或床位，不能接受组队邀请");
        verify(auditService).success(
                eq(invitee),
                eq("TEAM_INVITATION_ACCEPT"),
                eq("SELECTION_TEAM"),
                eq(31L),
                anyString(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.argThat(value ->
                        value instanceof Map<?, ?> map
                                && Integer.valueOf(2).equals(
                                        map.get("supersededInvitationCount"))));
    }

    @Test
    void rejectingInvitationOnlyClosesSelectedInvitation() {
        when(jdbc.queryForList(
                anyString(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of(
                        "id", 55L,
                        "team_id", 31L,
                        "invitee_student_id", 202L,
                        "batch_id", 7L,
                        "team_status", "FORMING")));
        when(jdbc.update(
                anyString(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class)))
                .thenReturn(1, 1);

        service.respond("token", false, invitee);

        verify(jdbc, org.mockito.Mockito.times(2)).update(
                anyString(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class));
        verify(formationService, never()).requireUnassigned(
                org.mockito.ArgumentMatchers.anyLong(),
                anyString());
        verify(auditService).success(
                eq(invitee),
                eq("TEAM_INVITATION_REJECT"),
                eq("SELECTION_TEAM"),
                eq(31L),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.anyMap());
    }
}
