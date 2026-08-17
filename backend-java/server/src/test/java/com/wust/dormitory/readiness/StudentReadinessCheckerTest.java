package com.wust.dormitory.readiness;

import com.wust.dormitory.readiness.mapper.SystemReadinessMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StudentReadinessCheckerTest {
    private static final ReadinessContext CONTEXT = new ReadinessContext(Instant.parse("2026-08-17T05:00:00Z"));

    @Test
    void unactivatedStudentsAreWarningButNotBlocking() {
        SystemReadinessMapper mapper = mock(SystemReadinessMapper.class);
        when(mapper.studentSummary()).thenReturn(Map.of(
                "totalStudents", 500L,
                "enabledStudents", 500L,
                "unactivatedStudents", 12L,
                "missingCriticalFields", 0L,
                "missingMajorMapping", 0L,
                "invalidDegreeLevel", 0L,
                "invalidGender", 0L));

        List<ReadinessCheckResult> results = new StudentReadinessChecker(mapper).check(CONTEXT);

        ReadinessCheckResult activation = results.stream()
                .filter(item -> item.code().equals("STUDENT_ACTIVATION"))
                .findFirst().orElseThrow();
        assertEquals(ReadinessSeverity.WARNING, activation.severity());
        assertFalse(activation.blocking());
        verifyNoInteractions();
    }
}
