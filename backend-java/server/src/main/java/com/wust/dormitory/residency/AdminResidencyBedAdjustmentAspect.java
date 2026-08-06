package com.wust.dormitory.residency;

import com.wust.dormitory.security.CurrentUser;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 管理员确认或调整床位后，将正式在住事实的来源统一标记为管理员修改。
 * 学生本人确认床位时保持原有来源不变。
 */
@Aspect
@Component
public class AdminResidencyBedAdjustmentAspect {
    private final NamedParameterJdbcTemplate jdbc;

    public AdminResidencyBedAdjustmentAspect(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @AfterReturning(
            value = "execution(* com.wust.dormitory.residency.ResidencyService.confirmBed(long,long,String,com.wust.dormitory.security.CurrentUser))"
                    + " && args(residencyId,bedId,reason,operator)")
    public void markAdminAdjustment(
            long residencyId,
            long bedId,
            String reason,
            CurrentUser operator) {
        if (operator == null || !operator.isAdmin()) {
            return;
        }
        jdbc.update("""
                UPDATE room_assignment
                SET source_selection_mode='DIRECT',
                    assignment_method='MANUAL_ADJUSTMENT',
                    updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=:residencyId AND assignment_status='ACTIVE'
                """, Map.of("residencyId", residencyId));
    }
}
