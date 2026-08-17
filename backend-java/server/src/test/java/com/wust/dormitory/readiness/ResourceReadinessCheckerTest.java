package com.wust.dormitory.readiness;

import com.wust.dormitory.readiness.mapper.SystemReadinessMapper;
import com.wust.dormitory.residency.mapper.RoomOccupancySnapshotMapper;
import com.wust.dormitory.residency.model.persistence.RoomOccupancySnapshotRow;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResourceReadinessCheckerTest {
    private static final ReadinessContext CONTEXT = new ReadinessContext(Instant.parse("2026-08-17T05:00:00Z"));

    @Test
    void exposesRoomIdsForExistingOccupancySnapshotReadModel() {
        assertDoesNotThrow(() -> SystemReadinessMapper.class.getDeclaredMethod("resourceRoomIds"));
    }

    @Test
    void blocksAnEmptySchool() {
        SystemReadinessMapper mapper = mock(SystemReadinessMapper.class);
        RoomOccupancySnapshotMapper snapshots = mock(RoomOccupancySnapshotMapper.class);
        when(mapper.resourceSummary()).thenReturn(Map.of(
                "campuses", 0L,
                "buildings", 0L,
                "rooms", 0L,
                "validBeds", 0L,
                "roomsWithoutBeds", 0L,
                "enabledRoomsWithoutBeds", 0L,
                "capacityMismatchRooms", 0L,
                "invalidRelations", 0L));
        when(mapper.resourceRoomIds()).thenReturn(List.of());

        List<ReadinessCheckResult> results = new ResourceReadinessChecker(mapper, snapshots).check(CONTEXT);

        assertTrue(results.stream().anyMatch(item -> item.code().equals("RESOURCE_BASELINE") && item.blocking()));
    }

    @Test
    void overlappingRoomAnomaliesAreReportedByCategory() {
        SystemReadinessMapper mapper = mock(SystemReadinessMapper.class);
        RoomOccupancySnapshotMapper snapshots = mock(RoomOccupancySnapshotMapper.class);
        when(mapper.resourceSummary()).thenReturn(Map.of(
                "campuses", 1L,
                "buildings", 1L,
                "rooms", 10L,
                "validBeds", 20L,
                "roomsWithoutBeds", 1L,
                "enabledRoomsWithoutBeds", 1L,
                "capacityMismatchRooms", 1L,
                "invalidRelations", 0L));
        when(mapper.resourceRoomIds()).thenReturn(List.of(1L));
        when(snapshots.findSnapshots(anyLong(), eq(List.of(1L))))
                .thenReturn(List.of(snapshot(1L, "ENABLED", 4, 5, 0, 0)));

        ReadinessCheckResult integrity = new ResourceReadinessChecker(mapper, snapshots).check(CONTEXT).stream()
                .filter(item -> item.code().equals("RESOURCE_INTEGRITY"))
                .findFirst().orElseThrow();

        assertTrue(integrity.blocking());
        assertEquals(4, integrity.evidence().get("issueCategoryCount"));
        assertEquals(1L, integrity.evidence().get("overCapacityRooms"));
        assertTrue(integrity.summary().contains("4 类"));
    }

    @Test
    void capacityInfoUsesExistingOccupancySnapshotSemantics() {
        SystemReadinessMapper mapper = mock(SystemReadinessMapper.class);
        RoomOccupancySnapshotMapper snapshots = mock(RoomOccupancySnapshotMapper.class);
        when(mapper.resourceSummary()).thenReturn(Map.of(
                "campuses", 2L,
                "buildings", 8L,
                "rooms", 2L,
                "validBeds", 8L,
                "roomsWithoutBeds", 0L,
                "enabledRoomsWithoutBeds", 0L,
                "capacityMismatchRooms", 0L,
                "invalidRelations", 0L));
        when(mapper.resourceRoomIds()).thenReturn(List.of(1L, 2L));
        when(snapshots.findSnapshots(anyLong(), eq(List.of(1L, 2L))))
                .thenReturn(List.of(
                        snapshot(1L, "ENABLED", 4, 2, 2, 4),
                        snapshot(2L, "ENABLED", 4, 1, 0, 2)));

        List<ReadinessCheckResult> results = new ResourceReadinessChecker(mapper, snapshots).check(CONTEXT);

        assertFalse(results.stream().anyMatch(ReadinessCheckResult::blocking));
        ReadinessCheckResult capacity = results.stream()
                .filter(item -> item.code().equals("BED_CAPACITY"))
                .findFirst().orElseThrow();
        assertEquals(6L, capacity.evidence().get("availableBeds"));
        assertEquals(3L, capacity.evidence().get("occupiedBeds"));
        assertEquals(4L, capacity.evidence().get("remainingBeds"));
        assertEquals(1L, capacity.evidence().get("confirmedBedAssignments"));
        assertEquals(2L, capacity.evidence().get("unconfirmedBedAssignments"));
        assertTrue(capacity.summary().contains("当前可用物理床位 6 个"));
        assertTrue(capacity.summary().contains("正式在住占用 3 人"));
        assertTrue(capacity.summary().contains("剩余资源容量 4 人"));
    }

    private RoomOccupancySnapshotRow snapshot(
            long id,
            String status,
            int capacity,
            int activeResidents,
            int unknownBeds,
            int availableBeds) {
        return new RoomOccupancySnapshotRow(
                id, String.valueOf(id), "STANDARD", capacity, "F", "MIXED", status,
                1L, 1, 1L, "B1", "楼栋", activeResidents, unknownBeds, availableBeds,
                null, null, null);
    }
}
