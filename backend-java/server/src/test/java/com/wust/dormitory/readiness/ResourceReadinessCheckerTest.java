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
    void overlappingRoomAnomaliesAreReportedByCategory() {
        SystemReadinessMapper mapper = mock(SystemReadinessMapper.class);
        when(mapper.resourceSummary()).thenReturn(Map.of(
                "campuses", 1L,
                "buildings", 1L,
                "rooms", 10L,
                "validBeds", 20L,
                "occupiedBeds", 5L,
                "roomsWithoutBeds", 1L,
                "enabledRoomsWithoutBeds", 1L,
                "capacityMismatchRooms", 1L,
                "invalidRelations", 0L));

        ReadinessCheckResult integrity = new ResourceReadinessChecker(mapper).check(CONTEXT).stream()
                .filter(item -> item.code().equals("RESOURCE_INTEGRITY"))
                .findFirst().orElseThrow();

        assertTrue(integrity.blocking());
        assertEquals(3, integrity.evidence().get("issueCategoryCount"));
        assertTrue(integrity.summary().contains("3 类"));
    }

    @Test
    void capacityInfoSeparatesResidentsFromConfirmedBedsInsteadOfInventingRemainingBeds() {
        SystemReadinessMapper mapper = mock(SystemReadinessMapper.class);
        when(mapper.resourceSummary()).thenReturn(Map.ofEntries(
                Map.entry("campuses", 2L),
                Map.entry("buildings", 8L),
                Map.entry("rooms", 120L),
                Map.entry("validBeds", 600L),
                Map.entry("occupiedBeds", 180L),
                Map.entry("activeResidents", 200L),
                Map.entry("unconfirmedResidents", 20L),
                Map.entry("roomsWithoutBeds", 0L),
                Map.entry("enabledRoomsWithoutBeds", 0L),
                Map.entry("capacityMismatchRooms", 0L),
                Map.entry("invalidRelations", 0L)));

        List<ReadinessCheckResult> results = new ResourceReadinessChecker(mapper).check(CONTEXT);

        assertFalse(results.stream().anyMatch(ReadinessCheckResult::blocking));
        ReadinessCheckResult capacity = results.stream()
                .filter(item -> item.code().equals("BED_CAPACITY"))
                .findFirst().orElseThrow();
        assertTrue(capacity.summary().contains("正式在住 200 人"));
        assertTrue(capacity.summary().contains("已确认床位 180 人"));
        assertTrue(capacity.summary().contains("未确认床位 20 人"));
        assertEquals(600L, capacity.evidence().get("enabledBeds"));
        assertEquals(200L, capacity.evidence().get("activeResidents"));
        assertEquals(180L, capacity.evidence().get("confirmedBedAssignments"));
        assertEquals(20L, capacity.evidence().get("unconfirmedBedAssignments"));
        assertFalse(capacity.evidence().containsKey("remainingBeds"));
    }
}
