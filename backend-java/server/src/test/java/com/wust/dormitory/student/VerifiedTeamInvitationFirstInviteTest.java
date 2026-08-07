package com.wust.dormitory.student;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerifiedTeamInvitationFirstInviteTest {
    @Test
    void firstVerifiedInvitationCreatesTeamBeforeWritingInvitation() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TeamService teamService = mock(TeamService.class);
        TeamFormationService formationService = mock(TeamFormationService.class);
        AuditService auditService = mock(AuditService.class);
        VerifiedTeamInvitationService service = new VerifiedTeamInvitationService(
                jdbc,
                teamService,
                formationService,
                auditService,
                new ObjectMapper());
        CurrentUser user = new CurrentUser(
                10L,
                100L,
                "202600000010",
                "发起学生",
                "STUDENT");

        when(formationService.currentBatchId(user.studentId())).thenReturn(7L);
        when(jdbc.queryForList(
                anyString(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of(
                        "id", 101L,
                        "student_number", "202600000011",
                        "student_name", "受邀学生",
                        "gender", "F")))
                .thenReturn(List.of());
        when(formationService.create(user)).thenReturn(Map.of(
                "id", 31L,
                "batch_id", 7L,
                "team_status", "FORMING",
                "member_role", "LEADER",
                "team_max_size", 5));
        when(jdbc.queryForList(anyString(), anyMap()))
                .thenReturn(List.of(Map.of("gender", "F")));
        when(jdbc.queryForObject(anyString(), anyMap(), eq(Integer.class)))
                .thenReturn(1);
        when(jdbc.queryForObject(
                anyString(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class),
                eq(Integer.class)))
                .thenReturn(0, 0);
        when(jdbc.update(
                anyString(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class)))
                .thenReturn(1);

        Map<String, Object> result = service.invite(
                "202600000011",
                "受邀学生",
                user);

        assertThat(result)
                .containsEntry("invited", true)
                .containsEntry("studentNumber", "202600000011")
                .containsEntry("studentName", "受邀学生");
        verify(formationService).create(user);
        verify(auditService).success(
                eq(user),
                eq("TEAM_INVITE"),
                eq("SELECTION_TEAM"),
                eq(31L),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                anyMap());
    }
}
