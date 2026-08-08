package com.wust.dormitory.roomchange;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.residency.ResidencyPolicyService;
import com.wust.dormitory.residency.ResidencyService;
import com.wust.dormitory.roomchange.mapper.RoomChangeMapper;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RoomChangeService {
    private static final Set<String> MODES = Set.of("DISABLED", "FREE", "APPROVAL_REQUIRED");

    private final RoomChangeMapper mapper;
    private final ResidencyPolicyService policy;
    private final ResidencyService residencyService;
    private final AuditService auditService;
    private final RoomChangeWorkflowSupport workflow;

    public RoomChangeService(
            RoomChangeMapper mapper,
            ResidencyPolicyService policy,
            ResidencyService residencyService,
            AuditService auditService,
            RoomChangeWorkflowSupport workflow) {
        this.mapper = mapper;
        this.policy = policy;
        this.residencyService = residencyService;
        this.auditService = auditService;
        this.workflow = workflow;
    }

    public Map<String, Object> policy() {
        String mode = currentMode();
        return Map.of(
                "mode", mode,
                "enabled", !"DISABLED".equals(mode),
                "requiresApproval", "APPROVAL_REQUIRED".equals(mode));
    }

    public List<Map<String, Object>> candidates(long studentId) {
        Map<String, Object> current = workflow.activeResidency(studentId, false);
        Map<String, Object> student = policy.student(studentId);
        return mapper.findCandidateRooms(
                number(current.get("room_id")),
                String.valueOf(student.get("gender")),
                String.valueOf(student.get("student_category")));
    }

    public List<Map<String, Object>> listMy(long studentId) {
        return mapper.findStudentRequests(studentId);
    }

    public List<Map<String, Object>> listAll(String status, String keyword) {
        return mapper.findAdminRequests(status, keyword == null ? "" : keyword.trim());
    }

    @Transactional
    public Map<String, Object> submit(
            long studentId,
            long targetRoomId,
            Long targetBedId,
            String reason,
            CurrentUser studentUser) {
        String mode = currentMode();
        if ("DISABLED".equals(mode)) {
            throw new BusinessException("ROOM_CHANGE_DISABLED", "学校当前未开放学生换寝", HttpStatus.CONFLICT);
        }
        String normalizedReason = requiredReason(reason);
        Map<String, Object> source = workflow.activeResidency(studentId, true);
        workflow.requireNoActiveRequest(studentId);
        workflow.validateTarget(studentId, targetRoomId, targetBedId, number(source.get("room_id")));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("studentId", studentId);
        request.put("sourceResidencyId", source.get("id"));
        request.put("sourceRoomId", source.get("room_id"));
        request.put("targetRoomId", targetRoomId);
        request.put("targetBedId", targetBedId);
        request.put("policyMode", mode);
        request.put("reason", normalizedReason);
        mapper.insertRequest(request);
        long requestId = number(request.get("id"));

        auditService.success(studentUser, "ROOM_CHANGE_REQUEST_CREATE", "ROOM_CHANGE_REQUEST",
                requestId, normalizedReason, source,
                Map.of("targetRoomId", targetRoomId, "policyMode", mode));
        if ("FREE".equals(mode)) {
            mapper.approveRequest(requestId, studentUser.userId(), "系统自由换寝自动批准");
            return executeRoomChange(requestId, studentUser, "学生自由换寝");
        }
        workflow.notifyStudent(studentId, "ROOM_CHANGE_SUBMITTED", requestId);
        return workflow.request(requestId);
    }

    @Transactional
    public Map<String, Object> approve(long requestId, String reason, CurrentUser admin) {
        Map<String, Object> request = workflow.requestForUpdate(requestId);
        requireStatus(request, "PENDING");
        String normalizedReason = requiredReason(reason);
        mapper.approveRequest(requestId, admin.userId(), normalizedReason);
        return executeRoomChange(requestId, admin, "管理员批准换寝：" + normalizedReason);
    }

    @Transactional
    public Map<String, Object> reject(long requestId, String reason, CurrentUser admin) {
        Map<String, Object> before = workflow.requestForUpdate(requestId);
        requireStatus(before, "PENDING");
        String normalizedReason = requiredReason(reason);
        mapper.rejectRequest(requestId, admin.userId(), normalizedReason);
        workflow.notifyStudent(number(before.get("student_id")), "ROOM_CHANGE_REJECTED", requestId);
        Map<String, Object> after = workflow.request(requestId);
        auditService.success(admin, "ROOM_CHANGE_REJECT", "ROOM_CHANGE_REQUEST",
                requestId, normalizedReason, before, after);
        return after;
    }

    @Transactional
    public Map<String, Object> cancel(long requestId, long studentId, String reason, CurrentUser studentUser) {
        Map<String, Object> before = workflow.requestForUpdate(requestId);
        if (number(before.get("student_id")) != studentId) {
            throw new BusinessException("ROOM_CHANGE_REQUEST_NOT_FOUND", "换寝申请不存在", HttpStatus.NOT_FOUND);
        }
        requireStatus(before, "PENDING");
        String normalizedReason = requiredReason(reason);
        mapper.cancelRequest(requestId, normalizedReason);
        Map<String, Object> after = workflow.request(requestId);
        auditService.success(studentUser, "ROOM_CHANGE_CANCEL", "ROOM_CHANGE_REQUEST",
                requestId, normalizedReason, before, after);
        return after;
    }

    @Transactional
    public Map<String, Object> updateSettings(String mode, String reason, CurrentUser admin) {
        if (!MODES.contains(mode)) {
            throw new BusinessException("ROOM_CHANGE_MODE_INVALID", "换寝模式无效");
        }
        String normalizedReason = requiredReason(reason);
        String before = currentMode();
        mapper.upsertPolicy(mode, admin.userId());
        Map<String, Object> after = policy();
        auditService.success(admin, "ROOM_CHANGE_POLICY_UPDATE", "SYSTEM_SETTING", 0L,
                normalizedReason, Map.of("mode", before), after);
        return after;
    }

    @Transactional
    public int cancelActiveRoomChanges(long studentId, String reason, CurrentUser operator) {
        String normalizedReason = requiredReason(reason);
        List<Long> ids = mapper.lockActiveRequestIds(studentId);
        if (ids.isEmpty()) return 0;
        int changed = mapper.cancelActiveRequests(studentId, normalizedReason, operator.userId());
        auditService.success(operator, "ROOM_CHANGE_CANCEL_FOR_RESET", "STUDENT", studentId,
                normalizedReason, Map.of("requestIds", ids), Map.of("cancelledCount", changed));
        return changed;
    }

    private Map<String, Object> executeRoomChange(long requestId, CurrentUser operator, String reason) {
        Map<String, Object> change = workflow.requestForUpdate(requestId);
        if (!"APPROVED".equals(String.valueOf(change.get("request_status")))) {
            throw new BusinessException("ROOM_CHANGE_NOT_APPROVED", "换寝申请尚未批准", HttpStatus.CONFLICT);
        }
        long studentId = number(change.get("student_id"));
        long sourceResidencyId = number(change.get("source_residency_id"));
        long sourceRoomId = number(change.get("source_room_id"));
        long targetRoomId = number(change.get("target_room_id"));
        Long targetBedId = nullableNumber(change.get("target_bed_id"));

        Map<String, Object> current = workflow.activeResidency(studentId, true);
        if (number(current.get("id")) != sourceResidencyId || number(current.get("room_id")) != sourceRoomId) {
            mapper.markFailed(requestId, "原住宿记录已经变化");
            throw new BusinessException("ROOM_CHANGE_SOURCE_CHANGED",
                    "原住宿记录已经变化，请重新提交换寝申请", HttpStatus.CONFLICT);
        }
        workflow.validateTarget(studentId, targetRoomId, targetBedId, sourceRoomId);
        residencyService.end(sourceResidencyId, reason, operator);
        Map<String, Object> newResidency = residencyService.assign(
                studentId, targetRoomId, targetBedId, null, null,
                "DIRECT", "MANUAL_ADJUSTMENT", reason, operator);
        mapper.markExecuted(requestId, number(newResidency.get("id")));
        workflow.notifyStudent(studentId, "ROOM_CHANGE_EXECUTED", requestId);
        Map<String, Object> after = workflow.request(requestId);
        auditService.success(operator, "ROOM_CHANGE_EXECUTE", "ROOM_CHANGE_REQUEST",
                requestId, reason, change, after);
        return after;
    }

    private String currentMode() {
        String mode = mapper.findPolicyMode();
        return MODES.contains(mode) ? mode : "DISABLED";
    }

    private void requireStatus(Map<String, Object> request, String status) {
        if (!status.equals(String.valueOf(request.get("request_status")))) {
            throw new BusinessException("ROOM_CHANGE_STATUS_INVALID",
                    "换寝申请当前状态不允许执行此操作", HttpStatus.CONFLICT);
        }
    }

    private String requiredReason(String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw new BusinessException("ROOM_CHANGE_REASON_REQUIRED", "请填写1至500个字符的原因");
        }
        return reason.trim();
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private Long nullableNumber(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
