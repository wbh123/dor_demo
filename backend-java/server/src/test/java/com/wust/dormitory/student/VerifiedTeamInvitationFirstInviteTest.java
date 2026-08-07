package com.wust.dormitory.student;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.student.mapper.VerifiedTeamInvitationMapper;
import org.junit.jupiter.api.Test;

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
        VerifiedTeamInvitationMapper mapper = mock(VerifiedTeamInvitationMapper.class);
        TeamService teamService = mock(TeamService.class);
        TeamFormationService formationService = mock(TeamFormationService.class);
        AuditService auditService = mock(AuditService.class);
        VerifiedTeamInvitationService service = new VerifiedTeamInvitationService(
                mapper,
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
        when(mapper.findEligibleInvitee(7L, "202600000011", "受邀学生"))
                .thenReturn(Map.of(
                        "id", 101L,
                        "student_number", "202600000011",
                        "student_name", "受邀学生",
                        "gender", "F"));
        when(mapper.findLeaderTeamForUpdate(7L, 100L))
                .thenReturn(null)
                .thenReturn(Map.of(
                        "id", 31L,
                        "batch_id", 7L,
                        "team_status", "FORMING",
                        "member_role", "LEADER",
                        "team_max_size", 5,
                        "inviter_gender", "F"));
        when(formationService.create(user)).thenReturn(Map.of(
                "id", 31L,
                "batch_id", 7L,
                "team_status", "FORMING",
                "member_role", "LEADER",
                "team_max_size", 5));
        when(mapper.findInvitationGuards(31L, 7L, 101L)).thenReturn(Map.of(
                "occupied_count", 1,
                "joined_elsewhere", 0,
                "duplicate_pending", 0));
        when(mapper.upsertInvitedMember(31L, 7L, 101L)).thenReturn(1);
        when(mapper.insertInvitation(eq(31L), eq(100L), eq(101L), anyString())).thenReturn(1);

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
