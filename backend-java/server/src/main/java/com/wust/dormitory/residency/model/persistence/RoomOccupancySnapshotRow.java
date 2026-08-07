package com.wust.dormitory.residency.model.persistence;

import java.util.LinkedHashMap;
import java.util.Map;

public record RoomOccupancySnapshotRow(
        long id,
        String roomNumber,
        String roomType,
        int capacity,
        String genderRestriction,
        String residentScope,
        String operationalStatus,
        long stateVersion,
        int floorNumber,
        long buildingId,
        String buildingCode,
        String buildingName,
        int activeResidents,
        int unknownBeds,
        Long conflictBatchId,
        String conflictBatchName,
        String conflictSelectionMode) {

    public Map<String, Object> roomMap() {
        Map<String, Object> room = new LinkedHashMap<>();
        room.put("id", id);
        room.put("room_number", roomNumber);
        room.put("room_type", roomType);
        room.put("capacity", capacity);
        room.put("gender_restriction", genderRestriction);
        room.put("resident_scope", residentScope);
        room.put("operational_status", operationalStatus);
        room.put("state_version", stateVersion);
        room.put("floor_number", floorNumber);
        room.put("building_id", buildingId);
        room.put("building_code", buildingCode);
        room.put("building_name", buildingName);
        return room;
    }

    public int remainingCapacity() {
        return Math.max(0, capacity - activeResidents);
    }
}
