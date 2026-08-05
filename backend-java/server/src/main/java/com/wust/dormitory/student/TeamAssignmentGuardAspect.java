package com.wust.dormitory.student;

import com.wust.dormitory.security.CurrentUser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Aspect
@Component
public class TeamAssignmentGuardAspect {
    private final NamedParameterJdbcTemplate jdbc;
    private final TeamFormationService formationService;
    private final TeamInvitationResponseService responseService;

    public TeamAssignmentGuardAspect(
            NamedParameterJdbcTemplate jdbc,
            TeamFormationService formationService,
            TeamInvitationResponseService responseService) {
        this.jdbc = jdbc;
        this.formationService = formationService;
        this.responseService = responseService;
    }

    @Around("execution(* com.wust.dormitory.student.TeamService.inviteTeammate(..)) && args(studentNumber,user)")
    public Object guardInvitation(
            ProceedingJoinPoint joinPoint,
            String studentNumber,
            CurrentUser user) throws Throwable {
        formationService.requireUnassigned(
                user.studentId(),
                "你已经确定寝室或床位，不能继续邀请队友");
        long batchId = formationService.currentBatchId(user.studentId());
        List<Long> invitees = jdbc.query("""
                SELECT student.id
                FROM student
                JOIN batch_student_eligibility eligibility
                  ON eligibility.student_id=student.id
                 AND eligibility.batch_id=:batchId
                WHERE student.student_number=:studentNumber
                  AND eligibility.eligibility_status='ELIGIBLE'
                """, Map.of(
                "batchId", batchId,
                "studentNumber", studentNumber),
                (resultSet, rowNumber) -> resultSet.getLong(1));
        if (!invitees.isEmpty()) {
            formationService.requireUnassigned(
                    invitees.getFirst(),
                    "该同学已经确定寝室或床位，不能参与组队");
        }
        return joinPoint.proceed();
    }

    @Around("execution(* com.wust.dormitory.student.TeamService.respondInvitation(..)) && args(token,accepted,user)")
    public Object handleInvitationResponse(
            ProceedingJoinPoint joinPoint,
            String token,
            boolean accepted,
            CurrentUser user) {
        responseService.respond(token, accepted, user);
        return null;
    }
}
