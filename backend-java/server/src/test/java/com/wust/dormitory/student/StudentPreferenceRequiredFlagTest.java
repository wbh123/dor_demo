package com.wust.dormitory.student;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudentPreferenceRequiredFlagTest {
    @Test
    void acceptsBooleanNumberAndStringFlagsReturnedByJdbcDrivers() {
        assertTrue(StudentPreferenceService.requiredFlag(Boolean.TRUE));
        assertTrue(StudentPreferenceService.requiredFlag(1));
        assertTrue(StudentPreferenceService.requiredFlag("1"));
        assertTrue(StudentPreferenceService.requiredFlag("true"));
        assertFalse(StudentPreferenceService.requiredFlag(Boolean.FALSE));
        assertFalse(StudentPreferenceService.requiredFlag(0));
        assertFalse(StudentPreferenceService.requiredFlag("false"));
        assertFalse(StudentPreferenceService.requiredFlag(null));
    }
}
