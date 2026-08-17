package com.wust.dormitory.readiness;

import com.wust.dormitory.readiness.mapper.SystemReadinessMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResourceReadinessCheckerTest {
    private static final ReadinessContext CONTEXT = new ReadinessContext(Instant.parse("2026-08-17T05:00:00Z"));

    @Test
    void blocksAnEmptySchool() {
        SystemReadinessMapper mapper = mock(SystemReadinessMapper.class);
        when(mapper.resourceSummary()).thenReturn(Map.of(
                "campuses", 0L,
                "buildings", 0L,
                "rooms", 0L,
                "validBeds", 0L,
                "occupiedBeds", 0L,
                "roomsWithoutBeds", 0L,
                "enabledRoomsWithoutBeds", 0L,
                "capacityMismatchRooms", 0L,
                "invalidRelations", 0L));

        List<ReadinessCheckResult> results = new ResourceReadinessChecker(mapper).check(CONTEXT);

        assertTrue(results.stream().anyMatch(item -> item.code().equals("RESOURCE_BASELINE") && item.blocking()));
    }

    @Test
    void passesHealthyResourcesAndReportsCapacity() {
        SystemReadinessMapper mapper = mock(SystemReadinessMapper.class);
        when(mapper.resourceSummary()).thenReturn(Map.of(
                "campuses", 2L,
                "buildings", 8L,
                "rooms", 120L,
                "validBeds", 600L,
                "occupiedBeds", 180L,
                "roomsWithoutBeds", 0L,
                "enabledRoomsWithoutBeds", 0L,
                "capacityMismatchRooms", 0L,
                "invalidRelations", 0L));

        List<ReadinessCheckResult> results = new ResourceReadinessChecker(mapper).check(CONTEXT);

        assertFalse(results.stream().anyMatch(ReadinessCheckResult::blocking));
        ReadinessCheckResult capacity = results.stream().filter(item -> item.code().equals("BED_CAPACITY")).findFirst().orElseThrow();
        assertTrue(capacity.summary().contains("剩余 420 个"));
    }
}
