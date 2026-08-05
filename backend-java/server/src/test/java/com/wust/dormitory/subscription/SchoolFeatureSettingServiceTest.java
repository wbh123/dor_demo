package com.wust.dormitory.subscription;

import com.wust.dormitory.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SchoolFeatureSettingServiceTest {
    @Test
    void schoolCannotEnableMissingSystemGrantOrUnimplementedFeature() {
        assertThrows(BusinessException.class, () -> SchoolFeatureSettingService.validateChange(
                state(true, false, true, "MEDIUM", 1), true, 1, "启用推荐", true));
        assertThrows(BusinessException.class, () -> SchoolFeatureSettingService.validateChange(
                state(false, true, true, "MEDIUM", 1), true, 1, "启用推荐", true));
    }

    @Test
    void nonControllableFeatureCannotBeChangedBySchool() {
        assertThrows(BusinessException.class, () -> SchoolFeatureSettingService.validateChange(
                state(true, true, false, "LOW", 1), false, 1, "尝试关闭", true));
    }

    @Test
    void changeRequiresReasonAndMatchingVersion() {
        assertThrows(BusinessException.class, () -> SchoolFeatureSettingService.validateChange(
                state(true, true, true, "LOW", 2), false, 2, "", true));
        assertThrows(BusinessException.class, () -> SchoolFeatureSettingService.validateChange(
                state(true, true, true, "LOW", 2), false, 1, "暂停使用", true));
    }

    @Test
    void highRiskChangeRequiresSecondConfirmation() {
        assertThrows(BusinessException.class, () -> SchoolFeatureSettingService.validateChange(
                state(true, true, true, "HIGH", 3), false, 3, "暂停分配", false));
        assertDoesNotThrow(() -> SchoolFeatureSettingService.validateChange(
                state(true, true, true, "HIGH", 3), false, 3, "暂停分配", true));
    }

    private SchoolFeatureSettingService.CurrentState state(
            boolean implemented,
            boolean systemGranted,
            boolean controllable,
            String risk,
            int version) {
        return new SchoolFeatureSettingService.CurrentState(
                "FEATURE", implemented, systemGranted, controllable, true,
                risk, version, true);
    }
}
