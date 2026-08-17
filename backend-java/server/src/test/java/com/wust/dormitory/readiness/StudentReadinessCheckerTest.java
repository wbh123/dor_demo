package com.wust.dormitory.readiness;

import com.wust.dormitory.readiness.mapper.SystemReadinessMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class StudentReadinessCheckerTest {
    private static final ReadinessContext CONTEXT = new ReadinessContext(Instant.parse("2026-08-17T05:00:00Z"));

    @Test
    void zeroStudentsBlockPilotReadiness() {
        SystemReadinessMapper mapper = mock(SystemReadinessMapper.class);
        when(mapper.studentSummary()).thenReturn(Map.of(
                "totalStudents", 0L,
                "enabledStudents", 0L,
                "unactivatedStudents", 0L,
                "invalidStudents", 0L,
                "missingCriticalFields", 0L,
                "missingMajorMapping", 0L,
                "invalidDegreeLevel", 0L,
                "invalidGender", 0L));

        ReadinessCheckResult quality = new StudentReadinessChecker(mapper).check(CONTEXT).getFirst();

        assertEquals("STUDENT_DATA_QUALITY", quality.code());
        assertEquals(ReadinessSeverity.ERROR, quality.severity());
        assertTrue(quality.blocking());
        verify(mapper).studentSummary();
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void unactivatedStudentsAreWarningButNotBlocking() {
        SystemReadinessMapper mapper = mock(SystemReadinessMapper.class);
        when(mapper.studentSummary()).thenReturn(Map.of(
                "totalStudents", 500L,
                "enabledStudents", 500L,
                "unactivatedStudents", 12L,
                "invalidStudents", 0L,
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
    }

    @Test
    void overlappingDataIssuesCountEachStudentOnlyOnce() {
        SystemReadinessMapper mapper = mock(SystemReadinessMapper.class);
        when(mapper.studentSummary()).thenReturn(Map.of(
                "totalStudents", 2L,
                "enabledStudents", 2L,
                "unactivatedStudents", 0L,
                "invalidStudents", 1L,
                "missingCriticalFields", 1L,
                "missingMajorMapping", 1L,
                "invalidDegreeLevel", 1L,
                "invalidGender", 1L));
        when(mapper.studentIssueSamples(10)).thenReturn(List.of(Map.of(
                "studentNumber", "202600000001")));

        ReadinessCheckResult quality = new StudentReadinessChecker(mapper).check(CONTEXT).getFirst();

        assertTrue(quality.blocking());
        assertEquals(1L, quality.evidence().get("invalidStudents"));
        assertTrue(quality.summary().contains("1 名"));
        assertEquals(1L, quality.evidence().get("missingCriticalFields"));
        assertEquals(1L, quality.evidence().get("missingMajorMapping"));
        assertEquals(1L, quality.evidence().get("invalidDegreeLevel"));
        assertEquals(1L, quality.evidence().get("invalidGender"));
    }
}
