package com.wust.dormitory.residency;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.residency.mapper.ResidencyMapper;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ResidencyService {
    private final ResidencyMapper mapper;
    private final ResidencyPolicyService policy;
    private final AuditService auditService;
    private final ResidencyHistoryWriter historyWriter;

    public ResidencyService(
            ResidencyMapper mapper,
            ResidencyPolicyService policy,
            AuditService auditService,
            ResidencyHistoryWriter historyWriter) {
        this.mapper = mapper;
        this.policy = policy;
        this.auditService = auditService;
        this.historyWriter = historyWriter;
    }

    public Map<String, Object> current(long studentId) {
        Map<String, Object> residency = mapper.findCurrentResidency(studentId);
        if (residency == null) return Map.of("resident", false);
        return Map.of("resident", true, "residency", residency);
    }

    public Map<String, Object> list(Long roomId, String keyword, String bedMappingStatus) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        List<Map<String, Object>> items = mapper.findResidencies(
                roomId, normalizedKeyword, bedMappingStatus);
        return Map.of(
                "items", items,
                "rooms", mapper.findRoomSummaries(),
                "total", items.size(),
                "unconfirmed", items.stream().filter(item -> item.get("bed_id") == null).count());
    }

    @Transactional
    public Map<String, Object> assign(
            long studentId,
            long roomId,
            Long bedId,
            Long batchId,
            Long teamId,
            String sourceMode,
            String method,
            String reason,
            CurrentUser operator) {
        String normalizedReason = requiredReason(reason);
        Map<String, Object> student = policy.student(studentId);
        Map<String, Object> room = policy.room(roomId, true);
        Map<String, Object> batch = batchId == null
                ? Map.of("separate_student_categories", 0, "selection_mode", "DIRECT")
                : policy.batch(batchId);
        policy.requireNoActiveResidency(studentId);
        policy.requireStudentEligibleForRoom(student, batch, room);
        if (batchId != null) {
            policy.requireRoomInBatch(batchId, roomId);
            policy.requireRoomLockedByBatch(batchId, roomId);
        }
        policy.requireRoomCapacity(roomId, 1);
        if (bedId != null) policy.requireAvailableBed(roomId, bedId);

        Map<String, Object> assignment = new LinkedHashMap<>();
        assignment.put("batchId", batchId);
        assignment.put("studentId", studentId);
        assignment.put("roomId", roomId);
        assignment.put("bedId", bedId);
        assignment.put("teamId", teamId);
        assignment.put("sourceMode", sourceMode);
        assignment.put("method", method);
        assignment.put("operatorId", operator.userId());
        mapper.insertAssignment(assignment);
        long residencyId = number(assignment.get("id"));

        historyWriter.append(
                residencyId, studentId, roomId, bedId,
                bedId == null ? "ROOM_ASSIGNED" : "BED_ASSIGNED",
                operator.userId(), normalizedReason, null,
                Map.of(
                        "batchId", batchId == null ? 0L : batchId,
                        "sourceMode", sourceMode,
                        "method", method,
                        "roomId", roomId,
                        "bedId", bedId == null ? 0L : bedId));
        auditService.success(
                operator,
                bedId == null ? "RESIDENCY_ROOM_ASSIGN" : "RESIDENCY_BED_ASSIGN",
                "ROOM_ASSIGNMENT",
                residencyId,
                normalizedReason,
                null,
                Map.of(
                        "studentId", studentId,
                        "roomId", roomId,
                        "bedId", bedId == null ? 0L : bedId,
                        "batchId", batchId == null ? 0L : batchId));
        return residency(residencyId);
    }

    @Transactional
    public Map<String, Object> synchronizeBedAssignment(
            long batchId,
            long studentId,
            long bedId,
            Long teamId,
            long assignedBy,
            String method) {
        Map<String, Object> bed = mapper.findBedRoom(bedId);
        if (bed == null) {
            throw new BusinessException("BED_NOT_FOUND", "床位不存在", HttpStatus.NOT_FOUND);
        }
        long roomId = number(bed.get("room_id"));
        Map<String, Object> existing = mapper.lockActiveResidency(studentId);
        if (existing != null) {
            if (number(existing.get("room_id")) != roomId) {
                throw new BusinessException(
                        "STUDENT_ALREADY_RESIDENT",
                        "学生已经归属其他寝室，不能确认当前床位",
                        HttpStatus.CONFLICT);
            }
            Long currentBedId = nullableLong(existing.get("bed_id"));
            if (currentBedId != null && currentBedId != bedId) {
                throw new BusinessException(
                        "STUDENT_BED_ALREADY_CONFIRMED",
                        "学生已经确认其他床位",
                        HttpStatus.CONFLICT);
            }
            if (currentBedId == null) {
                policy.requireAvailableBed(roomId, bedId);
                mapper.updateBedAssignment(number(existing.get("id")), bedId, method);
            }
            return residency(number(existing.get("id")));
        }
        CurrentUser synthetic = new CurrentUser(
                assignedBy, studentId, "system", "系统", "STUDENT");
        return assign(
                studentId, roomId, bedId, batchId, teamId,
                "BED", method, "同步选床结果为跨批次在住事实", synthetic);
    }

    @Transactional
    public Map<String, Object> confirmBed(
            long residencyId,
            long bedId,
            String reason,
            CurrentUser operator) {
        String normalizedReason = requiredReason(reason);
        Map<String, Object> before = residencyForUpdate(residencyId);
        if (!"ACTIVE".equals(String.valueOf(before.get("assignment_status")))) {
            throw new BusinessException("RESIDENCY_NOT_ACTIVE", "在住记录已经结束");
        }
        long roomId = number(before.get("room_id"));
        Long previousBedId = nullableLong(before.get("bed_id"));
        if (previousBedId != null && previousBedId == bedId) return residency(residencyId);

        requireBedAvailableForResidency(roomId, bedId, residencyId);
        mapper.confirmBed(residencyId, bedId);
        Map<String, Object> after = residency(residencyId);
        historyWriter.append(
                residencyId,
                number(before.get("student_id")),
                roomId,
                bedId,
                previousBedId == null ? "BED_CONFIRMED" : "BED_CHANGED",
                operator.userId(), normalizedReason, before, after);
        auditService.success(
                operator, "RESIDENCY_BED_CONFIRM", "ROOM_ASSIGNMENT",
                residencyId, normalizedReason, before, after);
        return after;
    }

    @Transactional
    public Map<String, Object> confirmOwnBed(long studentId, long bedId, CurrentUser operator) {
        Map<String, Object> current = mapper.lockActiveResidency(studentId);
        if (current == null) {
            throw new BusinessException("RESIDENCY_NOT_FOUND", "你当前没有有效寝室归属");
        }
        if (current.get("bed_id") != null) {
            throw new BusinessException(
                    "BED_ALREADY_CONFIRMED",
                    "实际床位已经确认，如需调整请联系管理员",
                    HttpStatus.CONFLICT);
        }
        return confirmBed(
                number(current.get("id")), bedId,
                "学生确认本人实际床位", operator);
    }

    @Transactional
    public Map<String, Object> end(long residencyId, String reason, CurrentUser operator) {
        String normalizedReason = requiredReason(reason);
        Map<String, Object> before = residencyForUpdate(residencyId);
        if (!"ACTIVE".equals(String.valueOf(before.get("assignment_status")))) {
            throw new BusinessException("RESIDENCY_NOT_ACTIVE", "在住记录已经结束");
        }
        mapper.endResidency(residencyId, normalizedReason);
        Map<String, Object> after = residency(residencyId);
        historyWriter.append(
                residencyId,
                number(before.get("student_id")),
                number(before.get("room_id")),
                nullableLong(before.get("bed_id")),
                "RESIDENCY_ENDED",
                operator.userId(), normalizedReason, before, after);
        auditService.success(
                operator, "RESIDENCY_END", "ROOM_ASSIGNMENT",
                residencyId, normalizedReason, before, after);
        return after;
    }

    public Map<String, Object> residency(long residencyId) {
        Map<String, Object> residency = mapper.findResidency(residencyId);
        if (residency == null) {
            throw new BusinessException(
                    "RESIDENCY_NOT_FOUND", "在住记录不存在", HttpStatus.NOT_FOUND);
        }
        return residency;
    }

    private void requireBedAvailableForResidency(long roomId, long bedId, long residencyId) {
        Map<String, Object> bed = mapper.lockBed(bedId);
        if (bed == null) {
            throw new BusinessException("BED_NOT_FOUND", "床位不存在", HttpStatus.NOT_FOUND);
        }
        if (number(bed.get("room_id")) != roomId) {
            throw new BusinessException(
                    "BED_NOT_IN_RESIDENCY_ROOM",
                    "所选床位不属于学生当前寝室",
                    HttpStatus.CONFLICT);
        }
        if (!"ENABLED".equals(String.valueOf(bed.get("operational_status")))) {
            throw new BusinessException(
                    "BED_NOT_AVAILABLE", "所选床位当前不可用", HttpStatus.CONFLICT);
        }
        if (mapper.countOtherActiveBedOccupants(bedId, residencyId) > 0) {
            throw new BusinessException(
                    "BED_ALREADY_OCCUPIED",
                    "所选床位已被其他在住学生占用",
                    HttpStatus.CONFLICT);
        }
    }

    private Map<String, Object> residencyForUpdate(long residencyId) {
        Map<String, Object> residency = mapper.lockResidency(residencyId);
        if (residency == null) {
            throw new BusinessException(
                    "RESIDENCY_NOT_FOUND", "在住记录不存在", HttpStatus.NOT_FOUND);
        }
        return residency;
    }

    private String requiredReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("CHANGE_REASON_REQUIRED", "必须填写操作原因");
        }
        return reason.trim();
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
