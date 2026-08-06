package com.wust.dormitory.residency;

import com.wust.dormitory.security.CurrentUser;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 管理员确认或调整床位后，将正式在住事实的来源统一标记为管理员修改。
 * 学生本人确认床位时保持原有来源不变。
 */
@Aspect
@Component
public class AdminResidencyBedAdjustmentAspect {
    private final ResidencyAdminSourceMapper sourceMapper;

    public AdminResidencyBedAdjustmentAspect(ResidencyAdminSourceMapper sourceMapper) {
        this.sourceMapper = sourceMapper;
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
        sourceMapper.markManualAdjustment(residencyId);
    }
}
