package com.wust.dormitory.admin;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.residency.AdminBedSwapService;
import com.wust.dormitory.residency.ResidencyService;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminStudentResidencyAdjustmentService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ResidencyService residencyService;
    private final AdminResidencyAdjustmentMapper adjustmentMapper;
    private final AdminBedSwapService swapService;

    public AdminStudentResidencyAdjustmentService(
            NamedParameterJdbcTemplate jdbc,
            ResidencyService residencyService,
            AdminResidencyAdjustmentMapper adjustmentMapper,
            AdminBedSwapService swapService) {
        this.jdbc = jdbc;
        this.residencyService = residencyService;
        this.adjustmentMapper = adjustmentMapper;
        this.swapService = swapService;
    }

    public Map<String, Object> context(long studentId) {
        Map<String, Object> student = student(studentId);
        Map<String, Object> currentResult = residencyService.current(studentId);
        @SuppressWarnings("unchecked")
        Map<String, Object> current = Boolean.TRUE.equals(currentResult.get("resident"))
                ? (Map<String, Object>) currentResult.get("residency")
                : Map.of();
        Long currentRoomId = numberOrNull(current.get("room_id"));
        Long currentBedId = numberOrNull(current.get("bed_id"));

        List<Map<String, Object>> beds = compatibleBeds(
                studentId,
                currentRoomId,
                currentBedId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("studentId", studentId);
        result.put("studentNumber", student.get("student_number"));
        result.put("studentName", student.get("student_name"));
        result.put("gender", student.get("gender"));
        result.put("studentCategory", student.get("student_category"));
        result.put("resident", !current.isEmpty());
        result.put("currentResidency", current);
        result.put("availableBeds", beds);
        result.put("beds", beds);
        result.put("hasSwapTargets", beds.stream().anyMatch(this::swapRequired));
        return result;
    }

    @Transactional
    public Map<String, Object> adjust(
            long studentId,
            long bedId,
            String reason,
            CurrentUser operator) {
        String normalizedReason = requiredReason(reason);
        student(studentId);
        Map<String, Object> currentResult = residencyService.current(studentId);
        @SuppressWarnings("unchecked")
        Map<String, Object> current = Boolean.TRUE.equals(currentResult.get("resident"))
                ? (Map<String, Object>) currentResult.get("residency")
                : Map.of();
        Long currentRoomId = numberOrNull(current.get("room_id"));
        Long currentBedId = numberOrNull(current.get("bed_id"));
        if (currentBedId != null && currentBedId == bedId) {
            throw new BusinessException(
                    "RESIDENCY_ADJUSTMENT_NO_CHANGE",
                    "目标床位与当前床位相同，请选择其他床位",
                    HttpStatus.CONFLICT);
        }

        Map<String, Object> target = compatibleBeds(studentId, currentRoomId, currentBedId).stream()
                .filter(item -> number(item.get("bed_id")) == bedId)
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "RESIDENCY_ADJUSTMENT_TARGET_UNAVAILABLE",
                        "目标床位不存在或不符合学生住宿范围",
                        HttpStatus.CONFLICT));
        if (!booleanValue(target.get("selectable"))) {
            throw new BusinessException(
                    "RESIDENCY_ADJUSTMENT_TARGET_LOCKED",
                    target.get("blocking_reason") == null
                            ? "目标床位当前不可调整"
                            : String.valueOf(target.get("blocking_reason")),
                    HttpStatus.CONFLICT);
        }
        if (swapRequired(target)) {
            return swapService.swapBeds(studentId, bedId, normalizedReason, operator);
        }

        if (!current.isEmpty()) {
            residencyService.end(
                    number(current.get("residency_id")),
                    normalizedReason,
                    operator);
        }
        Map<String, Object> assignment = residencyService.assign(
                studentId,
                number(target.get("room_id")),
                bedId,
                null,
                null,
                "DIRECT",
                "MANUAL_ADJUSTMENT",
                normalizedReason,
                operator);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("studentId", studentId);
        result.put("previousResidency", current);
        result.put("assignment", assignment);
        result.put("moved", !current.isEmpty());
        result.put("swapped", false);
        result.put("message", "学生寝室和床位已更新，来源记录为管理员修改");
        return result;
    }

    private Map<String, Object> student(long studentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, student_number, student_name, gender, student_category
                FROM student
                WHERE id=:studentId
                """, Map.of("studentId", studentId));
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "STUDENT_NOT_FOUND",
                    "学生不存在",
                    HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    private List<Map<String, Object>> compatibleBeds(
            long studentId,
            Long currentRoomId,
            Long currentBedId) {
        return adjustmentMapper.findCompatibleBeds(studentId, currentRoomId, currentBedId);
    }

    private boolean swapRequired(Map<String, Object> target) {
        return booleanValue(target.get("swap_required"));
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) return booleanValue;
        if (value instanceof Number number) return number.intValue() != 0;
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private String requiredReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < 2 || normalized.length() > 500) {
            throw new BusinessException(
                    "RESIDENCY_ADJUSTMENT_REASON_INVALID",
                    "调整原因长度必须为2至500个字符");
        }
        return normalized;
    }

    private long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    private Long numberOrNull(Object value) {
        return value == null ? null : number(value);
    }
}