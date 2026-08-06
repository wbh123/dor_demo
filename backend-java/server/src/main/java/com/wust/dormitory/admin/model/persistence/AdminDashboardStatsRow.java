package com.wust.dormitory.admin.model.persistence;

import java.util.LinkedHashMap;
import java.util.Map;

public record AdminDashboardStatsRow(
        Long majorCount,
        Long studentCount,
        Long maleStudentCount,
        Long femaleStudentCount,
        Long roomCount,
        Long bedCount,
        Long activeAssignmentCount,
        Long openBatchCount) {

    public Map<String, Object> asResponseMap() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("majorCount", majorCount);
        response.put("studentCount", studentCount);
        response.put("maleStudentCount", maleStudentCount);
        response.put("femaleStudentCount", femaleStudentCount);
        response.put("roomCount", roomCount);
        response.put("bedCount", bedCount);
        response.put("activeAssignmentCount", activeAssignmentCount);
        response.put("openBatchCount", openBatchCount);
        return response;
    }
}
