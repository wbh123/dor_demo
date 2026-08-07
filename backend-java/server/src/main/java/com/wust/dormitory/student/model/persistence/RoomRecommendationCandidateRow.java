package com.wust.dormitory.student.model.persistence;

import java.util.LinkedHashMap;
import java.util.Map;

public record RoomRecommendationCandidateRow(
        Long id,
        String roomNumber,
        String roomType,
        Integer capacity,
        String genderRestriction,
        String residentScope,
        String operationalStatus,
        Long stateVersion,
        Integer floorNumber,
        Long buildingId,
        String buildingCode,
        String buildingName,
        Integer activeResidentCount,
        Integer unknownBedResidentCount,
        Integer availableBedCount) {

    public Map<String, Object> toRoomMap() {
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

    public int activeResidents() {
        return activeResidentCount == null ? 0 : activeResidentCount;
    }

    public int unknownBedResidents() {
        return unknownBedResidentCount == null ? 0 : unknownBedResidentCount;
    }

    public int availableBeds() {
        return availableBedCount == null ? 0 : availableBedCount;
    }
}
