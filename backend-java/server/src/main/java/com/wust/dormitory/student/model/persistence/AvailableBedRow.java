package com.wust.dormitory.student.model.persistence;

import java.util.LinkedHashMap;
import java.util.Map;

public record AvailableBedRow(
        Long id,
        String bedCode,
        String bedType,
        Integer positionIndex) {

    public Map<String, Object> toMap() {
        Map<String, Object> bed = new LinkedHashMap<>();
        bed.put("id", id);
        bed.put("bed_code", bedCode);
        bed.put("bed_type", bedType);
        bed.put("position_index", positionIndex);
        return bed;
    }
}
