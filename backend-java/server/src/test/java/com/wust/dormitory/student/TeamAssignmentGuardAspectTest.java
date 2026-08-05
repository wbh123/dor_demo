package com.wust.dormitory.student;

import com.wust.dormitory.security.CurrentUser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamAssignmentGuardAspectTest {
    @Test
    void responseAdviceDelegatesToTransactionalResponseService() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TeamFormationService formation = mock(TeamFormationService.class);
        TeamInvitationResponseService response = mock(TeamInvitationResponseService.class);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        CurrentUser user = new CurrentUser(1L, 2L, "202600000002", "学生", "STUDENT");
        TeamAssignmentGuardAspect aspect = new TeamAssignmentGuardAspect(jdbc, formation, response);

        Object result = aspect.handleInvitationResponse(joinPoint, "token", true, user);

        assertThat(result).isNull();
        verify(response).respond("token", true, user);
    }

    @Test
    void invitationAdviceChecksBothStudentsBeforeProceeding() throws Throwable {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TeamFormationService formation = mock(TeamFormationService.class);
        TeamInvitationResponseService response = mock(TeamInvitationResponseService.class);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        CurrentUser user = new CurrentUser(1L, 2L, "202600000002", "学生", "STUDENT");
        TeamAssignmentGuardAspect aspect = new TeamAssignmentGuardAspect(jdbc, formation, response);
        when(formation.currentBatchId(user.studentId())).thenReturn(7L);
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of(9L));
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.guardInvitation(joinPoint, "202600000009", user);

        assertThat(result).isEqualTo("ok");
        verify(formation).requireUnassigned(user.studentId(), "你已经确定寝室或床位，不能继续邀请队友");
        verify(formation).requireUnassigned(9L, "该同学已经确定寝室或床位，不能参与组队");
        verify(joinPoint).proceed();
    }
}
