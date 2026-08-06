package com.wust.dormitory.admin.model.persistence;

import java.util.LinkedHashMap;
import java.util.Map;

public record RoomCatalogRow(
        Long id,
        Long buildingId,
        String buildingName,
        Integer floorNumber,
        String roomNumber,
        String roomType,
        Integer capacity,
        String genderRestriction,
        String educationLevelScope,
        String residentScope,
        String buildingGenderRestriction,
        String buildingEducationLevelScope,
        String buildingResidentScope,
        String operationalStatus,
        Long stateVersion,
        String remark,
        Long bedCount,
        Long enabledBedCount,
        Long disabledBedCount,
        Long maintenanceBedCount,
        Long activeResidentCount,
        Long confirmedBedCount,
        Long unconfirmedBedCount,
        Long remainingCapacity) {

    public Map<String, Object> asResponseMap() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        response.put("building_id", buildingId);
        response.put("building_name", buildingName);
        response.put("floor_number", floorNumber);
        response.put("room_number", roomNumber);
        response.put("room_type", roomType);
        response.put("capacity", capacity);
        response.put("gender_restriction", genderRestriction);
        response.put("education_level_scope", educationLevelScope);
        response.put("resident_scope", residentScope);
        response.put("building_gender_restriction", buildingGenderRestriction);
        response.put("building_education_level_scope", buildingEducationLevelScope);
        response.put("building_resident_scope", buildingResidentScope);
        response.put("operational_status", operationalStatus);
        response.put("state_version", stateVersion);
        response.put("remark", remark);
        response.put("bed_count", bedCount);
        response.put("enabled_bed_count", enabledBedCount);
        response.put("disabled_bed_count", disabledBedCount);
        response.put("maintenance_bed_count", maintenanceBedCount);
        response.put("active_resident_count", activeResidentCount);
        response.put("confirmed_bed_count", confirmedBedCount);
        response.put("unconfirmed_bed_count", unconfirmedBedCount);
        response.put("remaining_capacity", remainingCapacity);
        return response;
    }
}
