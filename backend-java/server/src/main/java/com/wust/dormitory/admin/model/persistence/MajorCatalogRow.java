package com.wust.dormitory.admin.model.persistence;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public record MajorCatalogRow(
        Long id,
        String majorCode,
        String majorName,
        Boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public Map<String, Object> asResponseMap() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        response.put("major_code", majorCode);
        response.put("major_name", majorName);
        response.put("enabled", enabled);
        response.put("created_at", createdAt);
        response.put("updated_at", updatedAt);
        return response;
    }
}
