package com.wust.dormitory.admin.model.persistence;

import java.util.LinkedHashMap;
import java.util.Map;

public record BuildingCatalogRow(
        Long id,
        String buildingCode,
        String buildingName,
        String genderRestriction,
        String educationLevelScope,
        String residentScope,
        Boolean enabled,
        String campusName,
        Long roomCount,
        Long bedCount) {

    public Map<String, Object> asResponseMap() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        response.put("building_code", buildingCode);
        response.put("building_name", buildingName);
        response.put("gender_restriction", genderRestriction);
        response.put("education_level_scope", educationLevelScope);
        response.put("resident_scope", residentScope);
        response.put("enabled", enabled);
        response.put("campus_name", campusName);
        response.put("room_count", roomCount);
        response.put("bed_count", bedCount);
        return response;
    }
}
