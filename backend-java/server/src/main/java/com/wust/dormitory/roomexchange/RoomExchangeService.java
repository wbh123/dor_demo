package com.wust.dormitory.roomexchange;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.residency.ResidencyService;
import com.wust.dormitory.roomexchange.mapper.RoomExchangeMapper;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RoomExchangeService {
    private static final Set<String> MODES = Set.of(
            "DISABLED", "MUTUAL_CONFIRMATION", "APPROVAL_REQUIRED");

    private final RoomExchangeMapper mapper;
    private final ResidencyService residencyService;
    private final AuditService auditService;
    private final RoomExchangeWorkflowSupport workflow;

    public RoomExchangeService(
            RoomExchangeMapper mapper,
            ResidencyService residencyService,
            AuditService auditService,
            RoomExchangeWorkflowSupport workflow) {
        this.mapper = mapper;
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

    public List<Map<String, Object>> candidates(long studentId, String studentNumber) {
        if ("DISABLED".equals(currentMode())) {
            throw new BusinessException(
                    "ROOM_EXCHANGE_DISABLED", "学校当前未开放学生寝室交换", HttpStatus.CONFLICT);
        }
        String normalized = requiredStudentNumber(studentNumber);
        return workflow.candidates(studentId, "%" + escapeLikePattern(normalized) + "%");
    }

    public List<Map<String, Object>> listMy(long studentId) {
        return mapper.findStudentRequests(studentId);
    }

    public List<Map<String, Object>> listAdmin(String status, String keyword) {
        return mapper.findAdminRequests(status, keyword == null ? "" : keyword.trim());
    }

    @Transactional
    public Map<String, Object> submit(
            long initiatorStudentId,
            long targetStudentId,
            String reason,
            CurrentUser initiator) {
        String mode = currentMode();
        if ("DISABLED".equals(mode)) {
            throw new BusinessException(
                    "ROOM_EXCHANGE_DISABLED", "学校当前未开放学生寝室交换", HttpStatus.CONFLICT);
        }
        if (initiatorStudentId == targetStudentId) {
            throw new BusinessException("ROOM_EXCHANGE_SAME_STUDENT", "不能向本人发起寝室交换");
        }
        String normalizedReason = requiredReason(reason);
        List<Map<String, Object>> residencies = workflow.lockActiveResidencies(
                initiatorStudentId, targetStudentId);
        Map<String, Object> initiatorResidency = workflow.residencyOf(residencies, initiatorStudentId);
        Map<String, Object> targetResidency = workflow.residencyOf(residencies, targetStudentId);
        workflow.requireCompatible(
                initiatorStudentId, initiatorResidency, targetStudentId, targetResidency);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("initiatorStudentId", initiatorStudentId);
        request.put("targetStudentId", targetStudentId);
        request.put("initiatorResidencyId", initiatorResidency.get("id"));
        request.put("targetResidencyId", targetResidency.get("id"));
        request.put("policyMode", mode);
        request.put("reason", normalizedReason);
        mapper.insertRequest(request);
        long exchangeId = number(request.get("id"));
        try {
            workflow.lockParticipant(exchangeId, initiatorStudentId, "INITIATOR");
            workflow.lockParticipant(exchangeId, targetStudentId, "TARGET");
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(
                    "ROOM_EXCHANGE_PARTICIPANT_BUSY",
                    "本人或对方已有进行中的寝室交换",
                    HttpStatus.CONFLICT);
        }
        Map<String, Object> after = workflow.request(exchangeId);
        auditService.success(initiator, "ROOM_EXCHANGE_CREATE", "ROOM_EXCHANGE_REQUEST",
                exchangeId, normalizedReason, null, after);
        return after;
    }

    @Transactional
    public Map<String, Object> respond(
            long exchangeId,
            long targetStudentId,
            boolean accepted,
            String reason,
            CurrentUser targetUser) {
        Map<String, Object> before = workflow.requestForUpdate(exchangeId);
        if (number(before.get("target_student_id")) != targetStudentId) {
            throw new BusinessException("ROOM_EXCHANGE_NOT_FOUND", "寝室交换邀请不存在", HttpStatus.NOT_FOUND);
        }
        requireStatus(before, "WAITING_TARGET");
        String normalizedReason = requiredReason(reason);
        if (!accepted) {
            mapper.rejectByTarget(exchangeId, normalizedReason);
            workflow.releaseParticipants(exchangeId);
            Map<String, Object> after = workflow.request(exchangeId);
            auditService.success(targetUser, "ROOM_EXCHANGE_REJECT_BY_TARGET",
                    "ROOM_EXCHANGE_REQUEST", exchangeId, normalizedReason, before, after);
            return after;
        }

        String mode = String.valueOf(before.get("policy_mode"));
        String nextStatus = "APPROVAL_REQUIRED".equals(mode) ? "PENDING_ADMIN" : "APPROVED";
        mapper.acceptByTarget(exchangeId, nextStatus, normalizedReason);
        if ("MUTUAL_CONFIRMATION".equals(mode)) {
            return executeExchange(exchangeId, targetUser, "双方确认后自动交换寝室床位");
        }
        Map<String, Object> after = workflow.request(exchangeId);
        auditService.success(targetUser, "ROOM_EXCHANGE_ACCEPT_BY_TARGET",
                "ROOM_EXCHANGE_REQUEST", exchangeId, normalizedReason, before, after);
        return after;
    }

    @Transactional
    public Map<String, Object> approve(long exchangeId, String reason, CurrentUser admin) {
        Map<String, Object> before = workflow.requestForUpdate(exchangeId);
        requireStatus(before, "PENDING_ADMIN");
        String normalizedReason = requiredReason(reason);
        mapper.approveRequest(exchangeId, admin.userId(), normalizedReason);
        return executeExchange(exchangeId, admin, "管理员批准寝室交换：" + normalizedReason);
    }

    @Transactional
    public Map<String, Object> reject(long exchangeId, String reason, CurrentUser admin) {
        Map<String, Object> before = workflow.requestForUpdate(exchangeId);
        requireStatus(before, "PENDING_ADMIN");
        String normalizedReason = requiredReason(reason);
        mapper.rejectByAdmin(exchangeId, admin.userId(), normalizedReason);
        workflow.releaseParticipants(exchangeId);
        Map<String, Object> after = workflow.request(exchangeId);
        auditService.success(admin, "ROOM_EXCHANGE_REJECT", "ROOM_EXCHANGE_REQUEST",
                exchangeId, normalizedReason, before, after);
        return after;
    }

    @Transactional
    public Map<String, Object> cancel(
            long exchangeId,
            long initiatorStudentId,
            String reason,
            CurrentUser initiator) {
        Map<String, Object> before = workflow.requestForUpdate(exchangeId);
        if (number(before.get("initiator_student_id")) != initiatorStudentId) {
            throw new BusinessException("ROOM_EXCHANGE_NOT_FOUND", "寝室交换申请不存在", HttpStatus.NOT_FOUND);
        }
        if (!Set.of("WAITING_TARGET", "PENDING_ADMIN")
                .contains(String.valueOf(before.get("request_status")))) {
            throw new BusinessException("ROOM_EXCHANGE_STATE_INVALID", "当前状态不能取消", HttpStatus.CONFLICT);
        }
        String normalizedReason = requiredReason(reason);
        mapper.cancelRequest(exchangeId, normalizedReason);
        workflow.releaseParticipants(exchangeId);
        Map<String, Object> after = workflow.request(exchangeId);
        auditService.success(initiator, "ROOM_EXCHANGE_CANCEL", "ROOM_EXCHANGE_REQUEST",
                exchangeId, normalizedReason, before, after);
        return after;
    }

    @Transactional
    public Map<String, Object> updateSettings(String mode, String reason, CurrentUser admin) {
        if (!MODES.contains(mode)) {
            throw new BusinessException("ROOM_EXCHANGE_MODE_INVALID", "寝室交换模式无效");
        }
        String normalizedReason = requiredReason(reason);
        String before = currentMode();
        mapper.upsertPolicy(mode, admin.userId());
        Map<String, Object> after = policy();
        auditService.success(admin, "ROOM_EXCHANGE_POLICY_UPDATE", "SYSTEM_SETTING", 0L,
                normalizedReason, Map.of("mode", before), after);
        return after;
    }

    private Map<String, Object> executeExchange(long exchangeId, CurrentUser operator, String reason) {
        Map<String, Object> before = workflow.requestForUpdate(exchangeId);
        requireStatus(before, "APPROVED");
        long initiatorStudentId = number(before.get("initiator_student_id"));
        long targetStudentId = number(before.get("target_student_id"));
        List<Map<String, Object>> current = workflow.lockActiveResidencies(
                initiatorStudentId, targetStudentId);
        Map<String, Object> initiatorResidency = workflow.residencyOf(current, initiatorStudentId);
        Map<String, Object> targetResidency = workflow.residencyOf(current, targetStudentId);
        if (number(initiatorResidency.get("id")) != number(before.get("initiator_residency_id"))
                || number(targetResidency.get("id")) != number(before.get("target_residency_id"))) {
            throw new BusinessException(
                    "ROOM_EXCHANGE_RESIDENCY_CHANGED",
                    "双方住宿记录已经变化，请重新发起交换",
                    HttpStatus.CONFLICT);
        }
        workflow.requireCompatible(
                initiatorStudentId, initiatorResidency, targetStudentId, targetResidency);

        residencyService.end(number(initiatorResidency.get("id")), reason, operator);
        residencyService.end(number(targetResidency.get("id")), reason, operator);
        Map<String, Object> initiatorNew = residencyService.assign(
                initiatorStudentId, number(targetResidency.get("room_id")),
                nullableNumber(targetResidency.get("bed_id")), null, null,
                "DIRECT", "ROOM_EXCHANGE", reason, operator);
        Map<String, Object> targetNew = residencyService.assign(
                targetStudentId, number(initiatorResidency.get("room_id")),
                nullableNumber(initiatorResidency.get("bed_id")), null, null,
                "DIRECT", "ROOM_EXCHANGE", reason, operator);
        mapper.markExecuted(exchangeId, number(initiatorNew.get("id")), number(targetNew.get("id")));
        workflow.releaseParticipants(exchangeId);
        Map<String, Object> after = workflow.request(exchangeId);
        auditService.success(operator, "ROOM_EXCHANGE_EXECUTE", "ROOM_EXCHANGE_REQUEST",
                exchangeId, reason, before, after);
        return after;
    }

    private String currentMode() {
        String mode = mapper.findPolicyMode();
        return mode == null ? "DISABLED" : mode;
    }

    private void requireStatus(Map<String, Object> request, String expected) {
        if (!expected.equals(String.valueOf(request.get("request_status")))) {
            throw new BusinessException(
                    "ROOM_EXCHANGE_STATE_INVALID", "寝室交换当前状态不允许该操作", HttpStatus.CONFLICT);
        }
    }

    private String requiredStudentNumber(String studentNumber) {
        String normalized = studentNumber == null ? "" : studentNumber.trim();
        if (normalized.isEmpty() || normalized.length() > 32) {
            throw new BusinessException(
                    "ROOM_EXCHANGE_STUDENT_NUMBER_INVALID", "请输入不超过32个字符的完整或部分学号");
        }
        return normalized;
    }

    private String escapeLikePattern(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private String requiredReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.isEmpty() || normalized.length() > 500) {
            throw new BusinessException("ROOM_EXCHANGE_REASON_INVALID", "原因必须填写且不能超过500个字符");
        }
        return normalized;
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private Long nullableNumber(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
