package com.wust.dormitory.residency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.common.json.JdbcJsonNormalizer;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AdminBedSwapService {
    private final AdminBedSwapMapper mapper;
    private final BedOccupancyQueryService occupancyQueryService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AdminBedSwapService(
            AdminBedSwapMapper mapper,
            BedOccupancyQueryService occupancyQueryService,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.occupancyQueryService = occupancyQueryService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> swapBeds(
            long studentId,
            long targetBedId,
            String reason,
            CurrentUser operator) {
        String normalizedReason = requiredReason(reason);
        Placement source = placementForUpdate(studentId);
        Map<String, Object> targetBed = requiredBed(targetBedId);
        BedOccupancyQueryService.BedOccupancy targetOccupancy =
                occupancyQueryService.describeRoom(number(targetBed.get("room_id")))
                        .get(targetBedId);
        if (targetOccupancy == null || !targetOccupancy.occupied()
                || targetOccupancy.occupantStudentId() == null) {
            throw new BusinessException(
                    "BED_SWAP_TARGET_EMPTY",
                    "目标床位当前没有可交换的学生",
                    HttpStatus.CONFLICT);
        }
        if (!"RESIDENCY".equals(targetOccupancy.occupancySource())
                && !"ALLOCATION".equals(targetOccupancy.occupancySource())) {
            throw new BusinessException(
                    "BED_SWAP_TARGET_LOCKED",
                    targetOccupancy.blockingReason() == null
                            ? "目标床位处于待确认或寝室交换处理中，暂不能交换"
                            : targetOccupancy.blockingReason(),
                    HttpStatus.CONFLICT);
        }
        long targetStudentId = targetOccupancy.occupantStudentId();
        if (targetStudentId == studentId) {
            throw new BusinessException(
                    "BED_SWAP_SAME_STUDENT",
                    "目标床位已经属于当前学生",
                    HttpStatus.CONFLICT);
        }

        Placement target = placementForUpdate(targetStudentId);
        if (source.bedId() == null || target.bedId() == null) {
            throw new BusinessException(
                    "BED_SWAP_PLACEMENT_INCOMPLETE",
                    "双方至少有一人的当前床位尚未形成有效分配，不能直接交换",
                    HttpStatus.CONFLICT);
        }
        if (source.bedId().equals(target.bedId())) {
            throw new BusinessException(
                    "BED_SWAP_ALREADY_SHARED",
                    "双方当前床位状态异常，请刷新后重试",
                    HttpStatus.CONFLICT);
        }
        if (!target.bedId().equals(targetBedId)) {
            throw new BusinessException(
                    "BED_SWAP_TARGET_CHANGED",
                    "目标学生的床位刚刚发生变化，请刷新后重试",
                    HttpStatus.CONFLICT);
        }

        Map<String, Object> sourceBed = requiredBed(source.bedId());
        long sourceRoomId = number(sourceBed.get("room_id"));
        long targetRoomId = number(targetBed.get("room_id"));
        requireCompatible(studentId, targetRoomId);
        requireCompatible(targetStudentId, sourceRoomId);

        Map<String, Object> sourceBefore = source.asAuditMap();
        Map<String, Object> targetBefore = target.asAuditMap();
        updatePlacement(source, targetRoomId, targetBedId, operator.userId());
        updatePlacement(target, sourceRoomId, source.bedId(), operator.userId());

        Map<String, Object> sourceAfter = placementResult(
                source,
                targetRoomId,
                targetBedId,
                String.valueOf(targetBed.get("building_name")),
                String.valueOf(targetBed.get("room_number")),
                String.valueOf(targetBed.get("bed_code")));
        Map<String, Object> targetAfter = placementResult(
                target,
                sourceRoomId,
                source.bedId(),
                String.valueOf(sourceBed.get("building_name")),
                String.valueOf(sourceBed.get("room_number")),
                String.valueOf(sourceBed.get("bed_code")));

        appendHistory(source, targetRoomId, targetBedId, operator, normalizedReason, sourceBefore, sourceAfter);
        appendHistory(target, sourceRoomId, source.bedId(), operator, normalizedReason, targetBefore, targetAfter);

        Map<String, Object> auditAfter = new LinkedHashMap<>();
        auditAfter.put("studentId", studentId);
        auditAfter.put("targetStudentId", targetStudentId);
        auditAfter.put("studentBedId", targetBedId);
        auditAfter.put("targetStudentBedId", source.bedId());
        Long auditEntityId = source.residencyId() == null
                ? Long.valueOf(studentId)
                : source.residencyId();
        auditService.success(
                operator,
                "RESIDENCY_BED_SWAP",
                "ROOM_ASSIGNMENT",
                auditEntityId,
                normalizedReason,
                Map.of("student", sourceBefore, "targetStudent", targetBefore),
                auditAfter);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("swapped", true);
        result.put("student", sourceAfter);
        result.put("targetStudent", targetAfter);
        result.put("message", "两名学生的床位已完成交换，来源均记录为管理员修改");
        return result;
    }

    private Placement placementForUpdate(long studentId) {
        Map<String, Object> residency = mapper.findActiveResidencyForUpdate(studentId);
        Map<String, Object> allocation = mapper.findActiveAllocationForUpdate(studentId);
        if ((residency == null || residency.isEmpty())
                && (allocation == null || allocation.isEmpty())) {
            throw new BusinessException(
                    "BED_SWAP_STUDENT_NOT_PLACED",
                    "学生当前没有可交换的在住或分配记录",
                    HttpStatus.CONFLICT);
        }
        Long residencyBedId = nullableNumber(residency == null ? null : residency.get("bed_id"));
        Long allocationBedId = nullableNumber(allocation == null ? null : allocation.get("bed_id"));
        Long bedId = residencyBedId != null ? residencyBedId : allocationBedId;
        Long roomId = nullableNumber(residency == null ? null : residency.get("room_id"));
        if (roomId == null && allocation != null) roomId = nullableNumber(allocation.get("room_id"));
        return new Placement(
                studentId,
                nullableNumber(residency == null ? null : residency.get("residency_id")),
                nullableNumber(allocation == null ? null : allocation.get("allocation_id")),
                roomId,
                bedId);
    }

    private Map<String, Object> requiredBed(long bedId) {
        Map<String, Object> bed = mapper.findBedForUpdate(bedId);
        if (bed == null || bed.isEmpty()) {
            throw new BusinessException("BED_NOT_FOUND", "床位不存在", HttpStatus.NOT_FOUND);
        }
        if (!"ENABLED".equals(String.valueOf(bed.get("operational_status")))
                || !"ENABLED".equals(String.valueOf(bed.get("room_status")))) {
            throw new BusinessException(
                    "BED_NOT_AVAILABLE",
                    "床位或寝室当前不可用",
                    HttpStatus.CONFLICT);
        }
        return bed;
    }

    private void requireCompatible(long studentId, long roomId) {
        if (mapper.countStudentRoomCompatible(studentId, roomId) != 1) {
            throw new BusinessException(
                    "BED_SWAP_ROOM_INCOMPATIBLE",
                    "交换后至少有一名学生不符合目标寝室的性别或学生类别范围",
                    HttpStatus.CONFLICT);
        }
    }

    private void updatePlacement(
            Placement placement,
            long roomId,
            long bedId,
            long operatorId) {
        if (placement.residencyId() != null
                && mapper.updateResidencyPlacement(
                        placement.residencyId(), roomId, bedId, operatorId) != 1) {
            throw new BusinessException(
                    "BED_SWAP_RESIDENCY_CHANGED",
                    "学生在住记录已经变化，请刷新后重试",
                    HttpStatus.CONFLICT);
        }
        if (placement.allocationId() != null) {
            mapper.updateActiveAllocation(placement.studentId(), bedId, operatorId);
        }
    }

    private void appendHistory(
            Placement placement,
            long roomId,
            long bedId,
            CurrentUser operator,
            String reason,
            Object previous,
            Object current) {
        if (placement.residencyId() == null) return;
        mapper.insertResidencyHistory(
                placement.residencyId(),
                placement.studentId(),
                roomId,
                bedId,
                operator.userId(),
                reason,
                json(previous),
                json(current));
    }

    private Map<String, Object> placementResult(
            Placement placement,
            long roomId,
            long bedId,
            String buildingName,
            String roomNumber,
            String bedCode) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("studentId", placement.studentId());
        result.put("residencyId", placement.residencyId());
        result.put("allocationId", placement.allocationId());
        result.put("roomId", roomId);
        result.put("bedId", bedId);
        result.put("buildingName", buildingName);
        result.put("roomNumber", roomNumber);
        result.put("bedCode", bedCode);
        result.put("assignmentMethod", "MANUAL_ADJUSTMENT");
        return result;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(JdbcJsonNormalizer.normalize(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("床位交换历史序列化失败", exception);
        }
    }

    private String requiredReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < 2 || normalized.length() > 500) {
            throw new BusinessException(
                    "BED_SWAP_REASON_INVALID",
                    "交换原因长度必须为2至500个字符");
        }
        return normalized;
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private Long nullableNumber(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private record Placement(
            long studentId,
            Long residencyId,
            Long allocationId,
            Long roomId,
            Long bedId) {
        Map<String, Object> asAuditMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("studentId", studentId);
            value.put("residencyId", residencyId);
            value.put("allocationId", allocationId);
            value.put("roomId", roomId);
            value.put("bedId", bedId);
            return value;
        }
    }
}
