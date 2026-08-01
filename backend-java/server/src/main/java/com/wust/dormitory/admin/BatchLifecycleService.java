package com.wust.dormitory.admin;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BatchLifecycleService {
    private static final Set<String> ACTIVE_STATUSES = Set.of("PUBLISHED", "OPEN", "PAUSED");

    private final NamedParameterJdbcTemplate jdbc;
    private final AdminService adminService;

    public BatchLifecycleService(NamedParameterJdbcTemplate jdbc, AdminService adminService) {
        this.jdbc = jdbc;
        this.adminService = adminService;
    }

    @Transactional
    public void changeStatus(long batchId, String targetStatus, CurrentUser operator) {
        String currentStatus = currentStatus(batchId);
        boolean enteringActiveState = !ACTIVE_STATUSES.contains(currentStatus)
                && ACTIVE_STATUSES.contains(targetStatus);
        boolean leavingActiveState = ACTIVE_STATUSES.contains(currentStatus)
                && !ACTIVE_STATUSES.contains(targetStatus);

        if (enteringActiveState) {
            acquireStudentLocks(batchId);
        }

        adminService.changeBatchStatus(batchId, targetStatus, operator);

        if (leavingActiveState) {
            jdbc.update(
                    "DELETE FROM active_batch_student_lock WHERE batch_id=:batchId",
                    Map.of("batchId", batchId)
            );
        }
    }

    private String currentStatus(long batchId) {
        List<String> statuses = jdbc.query(
                "SELECT batch_status FROM selection_batch WHERE id=:batchId FOR UPDATE",
                Map.of("batchId", batchId),
                (resultSet, rowNumber) -> resultSet.getString(1)
        );
        if (statuses.isEmpty()) {
            throw new BusinessException("BATCH_NOT_FOUND", "选寝批次不存在", HttpStatus.NOT_FOUND);
        }
        return statuses.getFirst();
    }

    private void acquireStudentLocks(long batchId) {
        try {
            jdbc.update("""
                    INSERT INTO active_batch_student_lock (student_id, batch_id)
                    SELECT student_id, batch_id
                    FROM batch_student_eligibility
                    WHERE batch_id=:batchId AND eligibility_status='ELIGIBLE'
                    """, Map.of("batchId", batchId));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(
                    "BATCH_STUDENT_ACTIVE_CONFLICT",
                    "部分学生已经参加另一个正在进行的选寝活动，请调整学生范围后重试",
                    HttpStatus.CONFLICT
            );
        }
    }
}
