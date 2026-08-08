package com.wust.dormitory.admin;

import com.wust.dormitory.admin.mapper.BatchLifecycleMapper;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.residency.BatchRoomLockService;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.subscription.EntitlementSnapshotService;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@Service
public class BatchLifecycleService {
    private static final Set<String> ACTIVE_STATUSES = Set.of("PUBLISHED", "OPEN", "PAUSED");
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "DRAFT", Set.of("PUBLISHED", "CANCELLED"),
            "PUBLISHED", Set.of("OPEN", "CANCELLED"),
            "OPEN", Set.of("PAUSED", "CLOSED"),
            "PAUSED", Set.of("OPEN", "CLOSED"),
            "CLOSED", Set.of("ALLOCATING", "FINISHED"),
            "ALLOCATING", Set.of("FINISHED", "CLOSED"));

    private final BatchLifecycleMapper batchLifecycleMapper;
    private final BatchScopeService batchScopeService;
    private final BatchRoomLockService roomLockService;
    private final FeatureAccessService featureAccessService;
    private final EntitlementSnapshotService entitlementSnapshotService;
    private final AuditService auditService;

    public BatchLifecycleService(
            BatchLifecycleMapper batchLifecycleMapper,
            BatchScopeService batchScopeService,
            BatchRoomLockService roomLockService,
            FeatureAccessService featureAccessService,
            EntitlementSnapshotService entitlementSnapshotService,
            AuditService auditService) {
        this.batchLifecycleMapper = batchLifecycleMapper;
        this.batchScopeService = batchScopeService;
        this.roomLockService = roomLockService;
        this.featureAccessService = featureAccessService;
        this.entitlementSnapshotService = entitlementSnapshotService;
        this.auditService = auditService;
    }

    @Transactional
    public void changeStatus(long batchId, String targetStatus, CurrentUser operator) {
        Map<String, Object> current = batchLifecycleMapper.lockBatch(batchId);
        if (current == null) {
            throw new BusinessException("BATCH_NOT_FOUND", "选寝批次不存在", HttpStatus.NOT_FOUND);
        }
        String currentStatus = String.valueOf(current.get("batch_status"));
        if (currentStatus.equals(targetStatus)) return;
        if (!TRANSITIONS.getOrDefault(currentStatus, Set.of()).contains(targetStatus)) {
            throw new BusinessException("BATCH_STATUS_INVALID", "不允许从" + currentStatus + "切换到" + targetStatus);
        }

        boolean enteringActiveState = !ACTIVE_STATUSES.contains(currentStatus)
                && ACTIVE_STATUSES.contains(targetStatus);
        boolean leavingActiveState = ACTIVE_STATUSES.contains(currentStatus)
                && !ACTIVE_STATUSES.contains(targetStatus);
        if (enteringActiveState) {
            if ("BED".equals(String.valueOf(current.get("selection_mode")))) {
                featureAccessService.require(FeatureCodes.P2_BED_SELECTION_MODE);
            }
            batchScopeService.requireReady(batchId);
            roomLockService.requirePublishable(batchId);
            acquireStudentLocks(batchId);
            roomLockService.acquire(batchId);
            entitlementSnapshotService.captureForBatch(batchId);
        }

        batchLifecycleMapper.updateStatus(batchId, targetStatus);
        if (leavingActiveState) {
            batchLifecycleMapper.deleteStudentLocks(batchId);
            roomLockService.release(batchId);
        }
        auditService.success(
                operator,
                "BATCH_STATUS_CHANGE",
                "SELECTION_BATCH",
                batchId,
                currentStatus + " -> " + targetStatus,
                current,
                Map.of("batchStatus", targetStatus));
    }

    private void acquireStudentLocks(long batchId) {
        try {
            batchLifecycleMapper.insertStudentLocks(batchId);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(
                    "BATCH_STUDENT_ACTIVE_CONFLICT",
                    "部分学生已经参加另一个正在进行的选寝活动，请调整学生范围后重试",
                    HttpStatus.CONFLICT);
        }
    }
}
