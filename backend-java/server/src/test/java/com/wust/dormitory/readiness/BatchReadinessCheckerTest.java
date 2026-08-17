package com.wust.dormitory.readiness;

import com.wust.dormitory.readiness.mapper.SystemReadinessMapper;
import com.wust.dormitory.residency.BatchRoomLockService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BatchReadinessCheckerTest {
    private static final ReadinessContext CONTEXT = new ReadinessContext(Instant.parse("2026-08-17T05:00:00Z"));

    @Test
    void noActiveBatchIsInformational() {
        SystemReadinessMapper mapper = mock(SystemReadinessMapper.class);
        BatchRoomLockService preflight = mock(BatchRoomLockService.class);
        when(mapper.activeBatches()).thenReturn(List.of());

        List<ReadinessCheckResult> results = new BatchReadinessChecker(mapper, preflight).check(CONTEXT);

        assertEquals(1, results.size());
        assertEquals(ReadinessSeverity.INFO, results.getFirst().severity());
    }

    @Test
    void blocksWhenParticipantsExceedOpenCapacity() {
        SystemReadinessMapper mapper = mock(SystemReadinessMapper.class);
        BatchRoomLockService preflight = mock(BatchRoomLockService.class);
        when(mapper.activeBatches()).thenReturn(List.of(Map.of(
                "id", 42L,
                "batchName", "2026 新生第一批",
                "batchStatus", "PUBLISHED")));
        when(mapper.participantCount(42L)).thenReturn(500L);
        when(preflight.preview(42L)).thenReturn(Map.of(
                "roomCount", 80,
                "availableCapacity", 480,
                "publishable", true,
                "studentConflictCount", 0));

        ReadinessCheckResult result = new BatchReadinessChecker(mapper, preflight)
                .check(CONTEXT).getFirst();

        assertTrue(result.blocking());
        assertEquals(ReadinessSeverity.ERROR, result.severity());
        assertEquals(Boolean.TRUE, result.evidence().get("capacityShortage"));
    }

    @Test
    void blocksWhenExistingPublishPreflightRejectsBatch() {
        SystemReadinessMapper mapper = mock(SystemReadinessMapper.class);
        BatchRoomLockService preflight = mock(BatchRoomLockService.class);
        when(mapper.activeBatches()).thenReturn(List.of(Map.of(
                "id", 7L,
                "batchName", "冲突批次",
                "batchStatus", "PAUSED")));
        when(mapper.participantCount(7L)).thenReturn(100L);
        when(preflight.preview(7L)).thenReturn(Map.of(
                "roomCount", 20,
                "availableCapacity", 120,
                "publishable", false,
                "studentConflictCount", 3));

        ReadinessCheckResult result = new BatchReadinessChecker(mapper, preflight)
                .check(CONTEXT).getFirst();

        assertTrue(result.blocking());
        assertEquals(3L, result.evidence().get("studentConflictCount"));
    }
}
