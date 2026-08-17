package com.wust.dormitory.readiness;

import com.wust.dormitory.readiness.mapper.SystemReadinessMapper;
import com.wust.dormitory.residency.mapper.RoomOccupancySnapshotMapper;
import com.wust.dormitory.residency.model.persistence.RoomOccupancySnapshotRow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ResourceReadinessChecker implements ReadinessChecker {
    private static final int SNAPSHOT_CHUNK_SIZE = 500;
    private static final long READINESS_BATCH_SENTINEL = 0L;

    private final SystemReadinessMapper mapper;
    private final RoomOccupancySnapshotMapper occupancySnapshots;

    public ResourceReadinessChecker(
            SystemReadinessMapper mapper,
            RoomOccupancySnapshotMapper occupancySnapshots) {
        this.mapper = mapper;
        this.occupancySnapshots = occupancySnapshots;
    }

    @Override
    public String category() {
        return "RESOURCE";
    }

    @Override
    public boolean critical() {
        return true;
    }

    @Override
    public List<ReadinessCheckResult> check(ReadinessContext context) {
        Map<String, Object> data = mapper.resourceSummary();
        long campuses = number(data, "campuses");
        long buildings = number(data, "buildings");
        long rooms = number(data, "rooms");
        long enabledBedRecords = number(data, "validBeds");
        long noBeds = number(data, "roomsWithoutBeds");
        long zeroEnabled = number(data, "enabledRoomsWithoutBeds");
        long mismatch = number(data, "capacityMismatchRooms");
        long invalidRelations = number(data, "invalidRelations");

        CapacitySummary capacity = capacitySummary(loadSnapshots());
        List<ReadinessCheckResult> results = new ArrayList<>();
        boolean empty = campuses == 0 || buildings == 0 || rooms == 0 || enabledBedRecords == 0;
        results.add(ReadinessCheckResult.of("RESOURCE_BASELINE", category(), "宿舍资源基础数据",
                empty ? ReadinessSeverity.ERROR : ReadinessSeverity.PASS, empty,
                empty ? "FAILED" : "PASSED",
                empty ? "校区、楼栋、房间或当前可用床位存在空数据，暂不适合开放。" : "宿舍资源基础数据已就绪。",
                Map.of("campuses", campuses, "buildings", buildings, "rooms", rooms, "validBeds", enabledBedRecords),
                empty ? "补全基础资源或恢复至少一个可用床位" : null, "/admin/dormitories", context.checkedAt()));

        int issueCategoryCount = positiveCount(
                noBeds,
                zeroEnabled,
                mismatch,
                invalidRelations,
                capacity.overCapacityRooms());
        Map<String, Object> integrityEvidence = new LinkedHashMap<>();
        integrityEvidence.put("issueCategoryCount", issueCategoryCount);
        integrityEvidence.put("roomsWithoutBeds", noBeds);
        integrityEvidence.put("enabledRoomsWithoutBeds", zeroEnabled);
        integrityEvidence.put("capacityMismatchRooms", mismatch);
        integrityEvidence.put("invalidRelations", invalidRelations);
        integrityEvidence.put("overCapacityRooms", capacity.overCapacityRooms());
        results.add(ReadinessCheckResult.of("RESOURCE_INTEGRITY", category(), "宿舍资源完整性",
                issueCategoryCount == 0 ? ReadinessSeverity.PASS : ReadinessSeverity.ERROR,
                issueCategoryCount > 0,
                issueCategoryCount == 0 ? "PASSED" : "FAILED",
                issueCategoryCount == 0
                        ? "未发现房间容量、床位、层级关联或正式在住超容量异常。"
                        : "发现 " + issueCategoryCount + " 类宿舍资源完整性异常；同一房间可能同时命中多个类别。",
                integrityEvidence,
                issueCategoryCount == 0 ? null : "前往宿舍资源管理按异常类别逐项修正", "/admin/dormitories", context.checkedAt()));

        results.add(ReadinessCheckResult.of(
                "BED_CAPACITY",
                category(),
                "当前床位与在住概况",
                ReadinessSeverity.INFO,
                false,
                "INFO",
                "当前可用物理床位 " + capacity.availableBeds() + " 个；正式在住 " + capacity.activeResidents()
                        + " 人；已占用具体床位 " + capacity.occupiedBeds() + " 个；剩余资源容量 "
                        + capacity.remainingBeds() + " 人；未确认具体床位 " + capacity.unconfirmedBedAssignments()
                        + " 人。实际活动可分配容量仍以批次预检为准。",
                Map.of(
                        "availableBeds", capacity.availableBeds(),
                        "activeResidents", capacity.activeResidents(),
                        "occupiedBeds", capacity.occupiedBeds(),
                        "remainingBeds", capacity.remainingBeds(),
                        "unconfirmedBedAssignments", capacity.unconfirmedBedAssignments(),
                        "enabledBedRecords", enabledBedRecords),
                null,
                "/admin/residencies",
                context.checkedAt()));
        return results;
    }

    private List<RoomOccupancySnapshotRow> loadSnapshots() {
        List<Long> roomIds = mapper.resourceRoomIds();
        if (roomIds == null || roomIds.isEmpty()) {
            return List.of();
        }
        List<RoomOccupancySnapshotRow> snapshots = new ArrayList<>(roomIds.size());
        for (int offset = 0; offset < roomIds.size(); offset += SNAPSHOT_CHUNK_SIZE) {
            int end = Math.min(roomIds.size(), offset + SNAPSHOT_CHUNK_SIZE);
            snapshots.addAll(occupancySnapshots.findSnapshots(
                    READINESS_BATCH_SENTINEL,
                    List.copyOf(roomIds.subList(offset, end))));
        }
        return snapshots;
    }

    private CapacitySummary capacitySummary(List<RoomOccupancySnapshotRow> snapshots) {
        long activeResidents = 0;
        long unconfirmedBedAssignments = 0;
        long availableBeds = 0;
        long remainingBeds = 0;
        long overCapacityRooms = 0;
        for (RoomOccupancySnapshotRow snapshot : snapshots) {
            activeResidents += snapshot.activeResidents();
            unconfirmedBedAssignments += snapshot.unknownBeds();
            if (snapshot.activeResidents() > snapshot.capacity()) {
                overCapacityRooms++;
            }
            if ("ENABLED".equals(snapshot.operationalStatus())) {
                availableBeds += snapshot.availableBeds();
                remainingBeds += Math.min(snapshot.availableBeds(), snapshot.remainingCapacity());
            }
        }
        long occupiedBeds = Math.max(0, activeResidents - unconfirmedBedAssignments);
        return new CapacitySummary(
                availableBeds,
                activeResidents,
                occupiedBeds,
                remainingBeds,
                unconfirmedBedAssignments,
                overCapacityRooms);
    }

    private int positiveCount(long... values) {
        int count = 0;
        for (long value : values) {
            if (value > 0) count++;
        }
        return count;
    }

    private long number(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private record CapacitySummary(
            long availableBeds,
            long activeResidents,
            long occupiedBeds,
            long remainingBeds,
            long unconfirmedBedAssignments,
            long overCapacityRooms) {
    }
}
