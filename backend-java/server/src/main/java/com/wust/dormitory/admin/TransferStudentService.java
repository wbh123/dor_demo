package com.wust.dormitory.admin;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.residency.BatchCapacityService;
import com.wust.dormitory.residency.ResidencyService;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TransferStudentService {
    private final StudentAdminService studentAdminService;
    private final ResidencyService residencyService;
    private final BatchCapacityService batchCapacityService;
    private final AuditService auditService;

    public TransferStudentService(
            StudentAdminService studentAdminService,
            ResidencyService residencyService,
            BatchCapacityService batchCapacityService,
            AuditService auditService) {
        this.studentAdminService = studentAdminService;
        this.residencyService = residencyService;
        this.batchCapacityService = batchCapacityService;
        this.auditService = auditService;
    }

    @Transactional
    public Map<String, Object> onboard(OnboardCommand command, CurrentUser operator) {
        OnboardCommand normalized = command.normalized();
        validate(normalized);
        long studentId = studentAdminService.saveStudent(
                null,
                new StudentAdminService.StudentCommand(
                        normalized.studentNumber(),
                        normalized.studentName(),
                        normalized.gender(),
                        normalized.majorId(),
                        normalized.nationalityCode(),
                        normalized.studentCategory(),
                        "TRANSFER_MANUAL",
                        normalized.phoneNumber()),
                operator);

        Map<String, Object> actionResult;
        switch (normalized.action()) {
            case "PROFILE_ONLY" -> actionResult = Map.of(
                    "action", "PROFILE_ONLY",
                    "message", "转学生资料已录入，后续可加入新批次或由管理员直接分配寝室");
            case "DIRECT_ASSIGNMENT" -> {
                if (normalized.directAssignment() == null) {
                    throw new BusinessException(
                            "DIRECT_ASSIGNMENT_REQUIRED",
                            "选择直接分配时必须指定寝室");
                }
                DirectAssignment direct = normalized.directAssignment();
                actionResult = residencyService.assign(
                        studentId,
                        direct.roomId(),
                        direct.bedId(),
                        null,
                        null,
                        "DIRECT",
                        direct.bedId() == null ? "DIRECT_ROOM" : "DIRECT_BED",
                        direct.reason(),
                        operator);
            }
            case "ADD_TO_BATCH" -> {
                if (normalized.batchId() == null) {
                    throw new BusinessException(
                            "BATCH_ENROLLMENT_REQUIRED",
                            "选择加入现有批次时必须指定批次");
                }
                actionResult = batchCapacityService.enroll(
                        normalized.batchId(),
                        studentId,
                        "TRANSFER_MANUAL",
                        normalized.reason(),
                        operator);
            }
            default -> throw new BusinessException(
                    "TRANSFER_ACTION_INVALID",
                    "转学生处理方式不合法");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("studentId", studentId);
        result.put("studentNumber", normalized.studentNumber());
        result.put("action", normalized.action());
        result.put("actionResult", actionResult);
        auditService.success(
                operator,
                "TRANSFER_STUDENT_ONBOARD",
                "STUDENT",
                studentId,
                normalized.reason(),
                null,
                Map.of(
                        "studentNumber", normalized.studentNumber(),
                        "studentCategory", normalized.studentCategory(),
                        "action", normalized.action()));
        return result;
    }

    private void validate(OnboardCommand command) {
        if (!java.util.List.of("PROFILE_ONLY", "DIRECT_ASSIGNMENT", "ADD_TO_BATCH")
                .contains(command.action())) {
            throw new BusinessException("TRANSFER_ACTION_INVALID", "转学生处理方式不合法");
        }
        if (command.reason() == null || command.reason().isBlank()) {
            throw new BusinessException("CHANGE_REASON_REQUIRED", "必须填写转学生录入原因");
        }
    }

    public record OnboardCommand(
            String studentNumber,
            String studentName,
            String gender,
            long majorId,
            String nationalityCode,
            String studentCategory,
            String phoneNumber,
            String action,
            DirectAssignment directAssignment,
            Long batchId,
            String reason) {

        OnboardCommand normalized() {
            return new OnboardCommand(
                    studentNumber == null ? "" : studentNumber.trim(),
                    studentName == null ? "" : studentName.trim(),
                    gender == null ? "" : gender.trim().toUpperCase(),
                    majorId,
                    nationalityCode == null ? "CN" : nationalityCode.trim().toUpperCase(),
                    studentCategory == null ? "DOMESTIC" : studentCategory.trim().toUpperCase(),
                    phoneNumber == null || phoneNumber.isBlank() ? null : phoneNumber.trim(),
                    action == null ? "PROFILE_ONLY" : action.trim().toUpperCase(),
                    directAssignment,
                    batchId,
                    reason == null ? "" : reason.trim());
        }
    }

    public record DirectAssignment(long roomId, Long bedId, String reason) {
    }
}
