package com.wust.dormitory.bedconfirmation;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.bedconfirmation.mapper.BedConfirmationMapper;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BedConfirmationService {
    private static final String PENDING = "PENDING";
    private static final Set<String> STUDENT_SELECTION_METHODS = Set.of(
            "ROOM_SELECT", "TEAM_ROOM_SELECT", "BED_SELECT", "TEAM_BED_SELECT");

    private final BedConfirmationMapper mapper;
    private final AuditService auditService;

    public BedConfirmationService(BedConfirmationMapper mapper, AuditService auditService) {
        this.mapper = mapper;
        this.auditService = auditService;
    }

    public Map<String, Object> my(long studentId) {
        Map<String, Object> residency = emptyIfNull(mapper.findCurrentResidency(studentId));
        if (residency.isEmpty()) return Map.of("resident", false, "eligible", false);
        boolean eligible = STUDENT_SELECTION_METHODS.contains(String.valueOf(residency.get("assignment_method")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resident", true);
        result.put("eligible", eligible);
        result.put("residency", residency);
        result.put("beds", mapper.findRoomBeds(number(residency, "room_id")));
        result.put("request", emptyIfNull(mapper.findPendingForResidency(number(residency, "residency_id"))));
        result.put("message", eligible
                ? "请选择你当前实际使用的床位，提交后由管理员按寝室核查"
                : "当前住宿结果不是学生选寝产生，不能自主申报实际床位");
        return result;
    }

    @Transactional
    public Map<String, Object> submit(long studentId, long bedId, String reason, CurrentUser user) {
        Map<String, Object> residency = emptyIfNull(mapper.lockCurrentResidency(studentId));
        if (residency.isEmpty()) {
            throw new BusinessException("RESIDENCY_NOT_FOUND", "当前没有有效住宿记录", HttpStatus.NOT_FOUND);
        }
        if (!STUDENT_SELECTION_METHODS.contains(String.valueOf(residency.get("assignment_method")))) {
            throw new BusinessException(
                    "BED_CONFIRMATION_NOT_AVAILABLE",
                    "只有通过学生选寝产生的住宿结果可以申报实际床位",
                    HttpStatus.FORBIDDEN);
        }
        String normalizedReason = requireReason(reason);
        long roomId = number(residency, "room_id");
        Map<String, Object> bed = emptyIfNull(mapper.lockBed(roomId, bedId));
        if (bed.isEmpty()) {
            throw new BusinessException("BED_NOT_FOUND", "床位不存在或不属于当前寝室", HttpStatus.NOT_FOUND);
        }
        if (!"ENABLED".equals(String.valueOf(bed.get("operational_status")))) {
            throw new BusinessException("BED_NOT_AVAILABLE", "该床位当前不可使用", HttpStatus.CONFLICT);
        }
        long residencyId = number(residency, "residency_id");
        if (mapper.findPendingForResidency(residencyId) != null) {
            throw new BusinessException(
                    "BED_CONFIRMATION_PENDING_EXISTS",
                    "当前已有一条待管理员核查的床位申报",
                    HttpStatus.CONFLICT);
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("residencyId", residencyId);
        request.put("studentId", studentId);
        request.put("roomId", roomId);
        request.put("bedId", bedId);
        request.put("reason", normalizedReason);
        mapper.insertRequest(request);
        long requestId = number(request, "id");
        Map<String, Object> after = request(requestId);
        auditService.success(user, "BED_CONFIRMATION_SUBMIT", "BED_CONFIRMATION_REQUEST",
                requestId, normalizedReason, null, after);
        return after;
    }

    @Transactional
    public Map<String, Object> cancel(long requestId, long studentId, String reason, CurrentUser user) {
        Map<String, Object> before = lockRequest(requestId);
        if (number(before, "student_id") != studentId || !PENDING.equals(before.get("request_status"))) {
            throw new BusinessException("BED_CONFIRMATION_NOT_FOUND", "待核查申请不存在", HttpStatus.NOT_FOUND);
        }
        String normalizedReason = requireReason(reason);
        mapper.cancelRequest(requestId, normalizedReason);
        Map<String, Object> after = request(requestId);
        auditService.success(user, "BED_CONFIRMATION_CANCEL", "BED_CONFIRMATION_REQUEST",
                requestId, normalizedReason, before, after);
        return after;
    }

    public List<Map<String, Object>> rooms(String keyword) {
        return mapper.findRooms(keyword == null ? "" : keyword.trim());
    }

    public Map<String, Object> room(long roomId) {
        Map<String, Object> room = roomInfo(roomId, false);
        Map<String, Object> result = new LinkedHashMap<>(room);
        result.put("beds", mapper.findRoomBeds(roomId));
        result.put("students", mapper.findRoomStudents(roomId));
        return result;
    }

    @Transactional
    public Map<String, Object> approveRoom(long roomId, String reason, CurrentUser admin) {
        String normalizedReason = requireReason(reason);
        roomInfo(roomId, true);
        List<Long> pendingIds = mapper.lockPendingRequests(roomId);
        if (pendingIds.isEmpty()) {
            throw new BusinessException("BED_CONFIRMATION_PENDING_EMPTY", "该寝室没有待核查申请", HttpStatus.NOT_FOUND);
        }
        mapper.lockActiveAssignments(roomId);
        mapper.lockRoomBeds(roomId);
        List<Map<String, Object>> candidates = mapper.findRoomApprovalCandidates(roomId);
        List<Map<String, Object>> approved = new ArrayList<>();
        List<Map<String, Object>> conflicts = new ArrayList<>();
        for (Map<String, Object> item : candidates) {
            long requestId = number(item, "id");
            long residencyId = number(item, "residency_id");
            long studentId = number(item, "student_id");
            long bedId = number(item, "declared_bed_id");
            String conflict = approvalConflict(item);
            if (conflict != null) {
                conflicts.add(Map.of("requestId", requestId, "bedId", bedId, "message", conflict));
                continue;
            }
            mapper.assignBed(residencyId, bedId);
            mapper.approveRequest(requestId, admin.userId(), normalizedReason);
            createNotification(studentId, "BED_CONFIRMATION_APPROVED",
                    "实际床位核查已通过", "管理员已核查并确认你申报的实际床位。",
                    Map.of("roomId", roomId, "bedId", bedId));
            approved.add(Map.of("requestId", requestId, "studentId", studentId, "bedId", bedId));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roomId", roomId);
        result.put("approved", approved);
        result.put("conflicts", conflicts);
        result.put("approvedCount", approved.size());
        result.put("conflictCount", conflicts.size());
        auditService.success(admin, "BED_CONFIRMATION_ROOM_APPROVE", "ROOM", roomId,
                normalizedReason, null, result);
        return result;
    }

    @Transactional
    public Map<String, Object> reject(long requestId, String reason, CurrentUser admin) {
        Map<String, Object> before = lockRequest(requestId);
        if (!PENDING.equals(String.valueOf(before.get("request_status")))) {
            throw new BusinessException("BED_CONFIRMATION_NOT_PENDING", "该申请已处理", HttpStatus.CONFLICT);
        }
        String normalizedReason = requireReason(reason);
        mapper.rejectRequest(requestId, admin.userId(), normalizedReason);
        createNotification(number(before, "student_id"), "BED_CONFIRMATION_REJECTED",
                "实际床位核查未通过", "管理员未通过你的实际床位申报，请核对后重新提交。",
                Map.of("requestId", requestId, "reason", normalizedReason));
        Map<String, Object> after = request(requestId);
        auditService.success(admin, "BED_CONFIRMATION_REJECT", "BED_CONFIRMATION_REQUEST",
                requestId, normalizedReason, before, after);
        return after;
    }

    private Map<String, Object> roomInfo(long roomId, boolean lock) {
        Map<String, Object> room = emptyIfNull(lock ? mapper.lockRoomInfo(roomId) : mapper.findRoomInfo(roomId));
        if (room.isEmpty()) throw new BusinessException("ROOM_NOT_FOUND", "寝室不存在", HttpStatus.NOT_FOUND);
        return room;
    }

    private Map<String, Object> lockRequest(long requestId) {
        Map<String, Object> request = emptyIfNull(mapper.lockRequest(requestId));
        if (request.isEmpty()) {
            throw new BusinessException("BED_CONFIRMATION_NOT_FOUND", "床位核查申请不存在", HttpStatus.NOT_FOUND);
        }
        return request;
    }

    private Map<String, Object> request(long requestId) {
        return emptyIfNull(mapper.findRequest(requestId));
    }

    private String approvalConflict(Map<String, Object> item) {
        if (!"ENABLED".equals(String.valueOf(item.get("operational_status")))) return "床位不可用";
        if (number(item, "declaration_count") > 1) return "多人申报同一床位";
        if (number(item, "occupied_by_other") > 0 && number(item, "occupied_by_self") == 0) {
            return "床位已被其他在住学生占用";
        }
        return null;
    }

    private void createNotification(
            long studentId,
            String type,
            String title,
            String message,
            Map<String, Object> parameters) {
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("studentId", studentId);
        notification.put("type", type);
        notification.put("title", title);
        notification.put("message", message);
        notification.put("parameters", toJson(parameters));
        mapper.insertStudentNotification(notification);
    }

    private String toJson(Map<String, Object> values) {
        return values.entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":\""
                        + String.valueOf(entry.getValue()).replace("\"", "\\\"") + "\"")
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }

    private String requireReason(String reason) {
        String value = reason == null ? "" : reason.trim();
        if (value.isEmpty()) throw new BusinessException("BED_CONFIRMATION_REASON_REQUIRED", "请填写操作原因");
        return value;
    }

    private Map<String, Object> emptyIfNull(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    private long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }
}
