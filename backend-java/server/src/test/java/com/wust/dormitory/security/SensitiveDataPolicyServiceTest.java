package com.wust.dormitory.security;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SensitiveDataPolicyServiceTest {
    private FeatureAccessService featureAccessService;
    private AuditService auditService;
    private SensitiveDataPolicyService service;

    @BeforeEach
    void setUp() {
        featureAccessService = mock(FeatureAccessService.class);
        auditService = mock(AuditService.class);
        service = new SensitiveDataPolicyService(featureAccessService, auditService);
    }

    @Test
    void administratorListsSensitiveDataMaskedByDefault() {
        CurrentUser admin = new CurrentUser(1L, null, "admin", "管理员", "ADMIN");

        SensitiveAccessLevel level = service.resolve(admin, 88L, false, null);

        assertEquals(SensitiveAccessLevel.MASKED, level);
        assertEquals("+861****78", service.apply(SensitiveField.Category.PHONE, "+8613812345678", level));
        assertEquals("已填写（内容已隐藏）", service.apply(SensitiveField.Category.PREFERENCE, "raw", level));
        verifyNoInteractions(featureAccessService, auditService);
    }

    @Test
    void studentCannotReadAnotherStudentsPhoneOrRawPreference() {
        CurrentUser student = new CurrentUser(2L, 20L, "202600000020", "学生", "STUDENT");

        SensitiveAccessLevel level = service.resolve(student, 21L, false, null);

        assertEquals(SensitiveAccessLevel.HIDDEN, level);
        assertNull(service.apply(SensitiveField.Category.PHONE, "13812345678", level));
        assertNull(service.apply(SensitiveField.Category.PREFERENCE, "raw", level));
    }

    @Test
    void fullViewRequiresReasonPermissionAndAudit() {
        CurrentUser admin = new CurrentUser(1L, null, "admin", "管理员", "ADMIN");

        assertThrows(BusinessException.class, () -> service.resolve(admin, 88L, true, ""));
        SensitiveAccessLevel level = service.resolve(admin, 88L, true, "业务核查");

        assertEquals(SensitiveAccessLevel.FULL, level);
        verify(featureAccessService).require(FeatureCodes.P2_SENSITIVE_DATA_VIEW);
        verify(auditService).success(eq(admin), eq("SENSITIVE_DATA_FULL_VIEW"), eq("STUDENT"),
                eq(88L), eq("业务核查"), any(), any());
    }

    @Test
    void fullExportUsesIndependentPermission() {
        CurrentUser admin = new CurrentUser(1L, null, "admin", "管理员", "ADMIN");

        service.requireSensitiveExport(admin, "审计调查");

        verify(featureAccessService).require(FeatureCodes.P2_SENSITIVE_DATA_EXPORT);
        verify(auditService).success(eq(admin), eq("SENSITIVE_DATA_EXPORT_REQUEST"),
                eq("EXPORT_TASK"), any(), eq("审计调查"), any(), any());
    }
}
