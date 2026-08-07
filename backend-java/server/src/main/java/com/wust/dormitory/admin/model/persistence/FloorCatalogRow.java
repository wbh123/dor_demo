package com.wust.dormitory.admin.model.persistence;

import java.util.LinkedHashMap;
import java.util.Map;

public record FloorCatalogRow(
        long id,
        long buildingId,
        int floorNumber,
        String floorName,
        boolean enabled) {

    public Map<String, Object> asResponseMap() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        response.put("building_id", buildingId);
        response.put("floor_number", floorNumber);
        response.put("floor_name", floorName);
        response.put("enabled", enabled);
        return response;
    }
}
