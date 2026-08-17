package com.wust.dormitory.readiness;

import com.wust.dormitory.readiness.mapper.SystemReadinessMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CoreReadinessCheckerCriticalityTest {
    @Test
    void studentDataCheckerIsCritical() {
        assertTrue(new StudentReadinessChecker(mock(SystemReadinessMapper.class)).critical());
    }

    @Test
    void authorizationCheckerIsCritical() {
        assertTrue(new AuthorizationReadinessChecker().critical());
    }
}
