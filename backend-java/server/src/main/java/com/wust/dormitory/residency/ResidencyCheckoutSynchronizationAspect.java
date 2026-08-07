package com.wust.dormitory.residency;

import com.wust.dormitory.security.CurrentUser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

@Aspect
@Component
public class ResidencyCheckoutSynchronizationAspect {
    private final ResidencyCheckoutMapper mapper;
    private final TransactionTemplate transactionTemplate;

    public ResidencyCheckoutSynchronizationAspect(
            ResidencyCheckoutMapper mapper,
            PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Around("execution(public java.util.Map com.wust.dormitory.residency.ResidencyService.end(long, String, com.wust.dormitory.security.CurrentUser)) && args(residencyId, reason, operator)")
    public Object synchronizeCheckout(
            ProceedingJoinPoint joinPoint,
            long residencyId,
            String reason,
            CurrentUser operator) throws Throwable {
        try {
            return transactionTemplate.execute(status -> {
                try {
                    Object result = joinPoint.proceed();
                    if (result instanceof Map<?, ?> ended) {
                        releaseCurrentAssignment(ended, reason, operator);
                    }
                    return result;
                } catch (Throwable throwable) {
                    status.setRollbackOnly();
                    throw new CheckoutProceedException(throwable);
                }
            });
        } catch (CheckoutProceedException exception) {
            throw exception.getCause();
        }
    }

    private void releaseCurrentAssignment(
            Map<?, ?> ended,
            String reason,
            CurrentUser operator) {
        Object batchValue = ended.get("batch_id");
        Object studentValue = ended.get("student_id");
        if (!(batchValue instanceof Number batch)
                || !(studentValue instanceof Number student)) {
            return;
        }
        long batchId = batch.longValue();
        long studentId = student.longValue();
        String normalizedReason = reason == null || reason.isBlank()
                ? "管理员办理退宿"
                : reason.trim();
        mapper.appendAssignmentCancellation(
                batchId,
                studentId,
                operator.userId(),
                normalizedReason);
        mapper.deleteActiveAssignment(batchId, studentId);
    }

    private static final class CheckoutProceedException extends RuntimeException {
        private CheckoutProceedException(Throwable cause) {
            super(cause);
        }
    }
}
