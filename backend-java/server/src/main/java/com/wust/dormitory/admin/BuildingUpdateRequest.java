package com.wust.dormitory.admin;

public record BuildingUpdateRequest(
        String buildingCode,
        String buildingName,
        String gender,
        String educationLevelScope,
        String residentScope,
        Integer floorCount,
        Boolean enabled,
        String reason) {
}
