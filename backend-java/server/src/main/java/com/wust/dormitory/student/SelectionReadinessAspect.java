package com.wust.dormitory.student;

import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.selection.SelectionPolicyService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect @Component
public class SelectionReadinessAspect {
    private final SelectionPolicyService policies;
    public SelectionReadinessAspect(SelectionPolicyService policies){this.policies=policies;}
    private void check(long batchId,CurrentUser user){policies.requireSelectionReady(batchId,user.studentId());}
    @Around("execution(* com.wust.dormitory.residency.RoomSelectionService.selectPersonal(..)) && args(batchId,roomId,user)") public Object personal(ProceedingJoinPoint jp,long batchId,long roomId,CurrentUser user)throws Throwable{check(batchId,user);return jp.proceed();}
    @Around("execution(* com.wust.dormitory.residency.RoomSelectionService.selectTeam(..)) && args(batchId,teamId,roomId,user)") public Object team(ProceedingJoinPoint jp,long batchId,long teamId,long roomId,CurrentUser user)throws Throwable{check(batchId,user);return jp.proceed();}
    @Around("execution(* com.wust.dormitory.student.StudentService.hold(..)) && args(batchId,bedId,user)") public Object hold(ProceedingJoinPoint jp,long batchId,long bedId,CurrentUser user)throws Throwable{check(batchId,user);return jp.proceed();}
    @Around("execution(* com.wust.dormitory.student.StudentService.confirm(..)) && args(batchId,bedId,token,user)") public Object confirm(ProceedingJoinPoint jp,long batchId,long bedId,String token,CurrentUser user)throws Throwable{check(batchId,user);return jp.proceed();}
    @Around("execution(* com.wust.dormitory.student.StudentService.holdTeam(..)) && args(batchId,teamId,bedIds,user)") public Object holdTeam(ProceedingJoinPoint jp,long batchId,long teamId,Object bedIds,CurrentUser user)throws Throwable{check(batchId,user);return jp.proceed();}
}
