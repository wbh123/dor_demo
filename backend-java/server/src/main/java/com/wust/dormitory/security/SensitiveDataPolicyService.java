package com.wust.dormitory.security;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SensitiveDataPolicyService {
    private final FeatureAccessService featureAccessService;
    private final AuditService auditService;

    public SensitiveDataPolicyService(
            FeatureAccessService featureAccessService,
            AuditService auditService) {
        this.featureAccessService = featureAccessService;
        this.auditService = auditService;
    }

    public SensitiveAccessLevel resolve(
            CurrentUser viewer,
            Long subjectStudentId,
            boolean requestFull,
            String reason) {
        if (viewer == null) {
            return SensitiveAccessLevel.HIDDEN;
        }
        boolean studentViewer = viewer.isStudent();
        if (studentViewer && (subjectStudentId == null || !subjectStudentId.equals(viewer.studentId()))) {
            return SensitiveAccessLevel.HIDDEN;
        }
        if (!requestFull) {
            return viewer.isAdmin() ? SensitiveAccessLevel.MASKED : SensitiveAccessLevel.FULL;
        }
        requireReason(reason);
        featureAccessService.require(FeatureCodes.P2_SENSITIVE_DATA_VIEW);
        auditService.success(
                viewer,
                "SENSITIVE_DATA_FULL_VIEW",
                "STUDENT",
                subjectStudentId,
                reason.trim(),
                null,
                Map.of("accessLevel", SensitiveAccessLevel.FULL.name()));
        return SensitiveAccessLevel.FULL;
    }

    public void requireSensitiveExport(CurrentUser viewer, String reason) {
        requireReason(reason);
        featureAccessService.require(FeatureCodes.P2_SENSITIVE_DATA_EXPORT);
        auditService.success(
                viewer,
                "SENSITIVE_DATA_EXPORT_REQUEST",
                "EXPORT_TASK",
                null,
                reason.trim(),
                null,
                Map.of("accessLevel", SensitiveAccessLevel.FULL.name()));
    }

    public String apply(
            SensitiveField.Category category,
            Object rawValue,
            SensitiveAccessLevel level) {
        if (rawValue == null) {
            return null;
        }
        String value = String.valueOf(rawValue);
        if (level == SensitiveAccessLevel.FULL) {
            return value;
        }
        if (level == SensitiveAccessLevel.HIDDEN) {
            return null;
        }
        return switch (category) {
            case PHONE -> maskPhone(value);
            case PREFERENCE -> maskPreference(value);
            case IDENTITY -> maskIdentity(value);
            case ADDRESS -> value.length() <= 4 ? "****" : value.substring(0, 2) + "***";
            case NETWORK_ADDRESS -> maskNetworkAddress(value);
            case FREE_TEXT -> "已脱敏";
        };
    }

    public Map<String, Object> sanitizeStudent(
            Map<String, Object> source,
            SensitiveAccessLevel level) {
        Map<String, Object> result = new LinkedHashMap<>(source);
        result.put("phone", apply(SensitiveField.Category.PHONE, source.get("phone"), level));
        result.put("raw_preferences", apply(
                SensitiveField.Category.PREFERENCE,
                source.get("raw_preferences"),
                level));
        result.put("identity_number", apply(
                SensitiveField.Category.IDENTITY,
                source.get("identity_number"),
                level));
        return result;
    }

    public String maskPhone(String value) {
        String normalized = value.replaceAll("\\s+", "");
        if (normalized.length() <= 4) {
            return "****";
        }
        int prefix = Math.min(normalized.startsWith("+") ? 4 : 3, normalized.length() - 2);
        return normalized.substring(0, prefix)
                + "****"
                + normalized.substring(normalized.length() - 2);
    }

    public String maskPreference(String value) {
        return value.isBlank() ? "" : "已填写（内容已隐藏）";
    }

    private String maskIdentity(String value) {
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "********" + value.substring(value.length() - 2);
    }

    private String maskNetworkAddress(String value) {
        int lastDot = value.lastIndexOf('.');
        if (lastDot > 0) {
            return value.substring(0, lastDot + 1) + "*";
        }
        return "已隐藏";
    }

    public String requireReason(String reason) {
        if (reason == null || reason.trim().length() < 2) {
            throw new BusinessException(
                    "SENSITIVE_REASON_REQUIRED",
                    "查看或导出完整敏感数据必须填写原因",
                    HttpStatus.BAD_REQUEST);
        }
        return reason.trim();
    }
}
