package com.wust.dormitory.student;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Aspect @Component
public class TeamAssignmentGuardAspect {
    private final NamedParameterJdbcTemplate jdbc; private final TeamFormationService formation;
    public TeamAssignmentGuardAspect(NamedParameterJdbcTemplate jdbc,TeamFormationService formation){this.jdbc=jdbc;this.formation=formation;}
    @Around("execution(* com.wust.dormitory.student.TeamService.inviteTeammate(..)) && args(studentNumber,user)")
    public Object invite(ProceedingJoinPoint jp,String studentNumber,CurrentUser user)throws Throwable{
        formation.requireUnassigned(user.studentId(),"你已经确定寝室或床位，不能继续邀请队友");
        long batchId=formation.currentBatchId(user.studentId());
        Integer memberships=jdbc.queryForObject("SELECT COUNT(*) FROM selection_team_member m JOIN selection_team t ON t.id=m.team_id WHERE m.batch_id=:batchId AND m.student_id=:studentId AND m.active_marker=1 AND m.member_role='LEADER' AND t.team_status='FORMING'",Map.of("batchId",batchId,"studentId",user.studentId()),Integer.class);
        if(memberships==null||memberships==0) throw new BusinessException("TEAM_FORMING_REQUIRED","请先创建处于组队中的队伍",HttpStatus.CONFLICT);
        List<Long> invitees=jdbc.query("SELECT s.id FROM student s JOIN batch_student_eligibility e ON e.student_id=s.id AND e.batch_id=:batchId WHERE s.student_number=:number AND e.eligibility_status='ELIGIBLE'",Map.of("batchId",batchId,"number",studentNumber),(rs,n)->rs.getLong(1));
        if(!invitees.isEmpty()) formation.requireUnassigned(invitees.getFirst(),"该同学已经确定寝室或床位，不能参与组队");
        return jp.proceed();
    }
    @Around("execution(* com.wust.dormitory.student.TeamService.respondInvitation(..)) && args(token,accepted,user)")
    public Object respond(ProceedingJoinPoint jp,String token,boolean accepted,CurrentUser user)throws Throwable{
        if(accepted) formation.requireUnassigned(user.studentId(),"你已经确定寝室或床位，不能接受组队邀请");
        return jp.proceed();
    }
}
