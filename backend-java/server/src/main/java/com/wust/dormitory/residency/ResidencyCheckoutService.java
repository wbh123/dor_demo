package com.wust.dormitory.residency;

import com.wust.dormitory.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class ResidencyCheckoutService {
    private final ResidencyService residencyService;
    private final ResidencyCheckoutMapper mapper;

    public ResidencyCheckoutService(
            ResidencyService residencyService,
            ResidencyCheckoutMapper mapper) {
        this.residencyService = residencyService;
        this.mapper = mapper;
    }

    @Transactional
    public Map<String, Object> checkout(
            long residencyId,
            String reason,
            CurrentUser operator) {
        Map<String, Object> ended = residencyService.end(residencyId, reason, operator);
        Object batchValue = ended.get("batch_id");
        Object studentValue = ended.get("student_id");
        if (batchValue instanceof Number batch && studentValue instanceof Number student) {
            long batchId = batch.longValue();
            long studentId = student.longValue();
            mapper.appendAssignmentCancellation(
                    batchId,
                    studentId,
                    operator.userId(),
                    reason == null ? "管理员办理退宿" : reason.trim());
            mapper.deleteActiveAssignment(batchId, studentId);
        }
        return ended;
    }
}
