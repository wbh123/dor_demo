package com.wust.dormitory.readiness;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationCapabilityReadinessTest {
    @Test
    void acceptsEitherSelfSelectionOrUnifiedAllocationAsAUsableAllocationPath() {
        assertTrue(AllocationCapabilityReadiness.hasUsablePath(true, false));
        assertTrue(AllocationCapabilityReadiness.hasUsablePath(false, true));
        assertTrue(AllocationCapabilityReadiness.hasUsablePath(true, true));
    }

    @Test
    void blocksOnlyWhenEveryAllocationPathIsUnavailable() {
        assertFalse(AllocationCapabilityReadiness.hasUsablePath(false, false));
    }
}
