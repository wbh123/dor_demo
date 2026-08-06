package com.wust.dormitory.admin.model.persistence;

import java.util.LinkedHashMap;
import java.util.Map;

public record StudentCatalogRow(
        Long id,
        String studentNumber,
        String studentName,
        String gender,
        Long majorId,
        String majorCode,
        String majorName,
        String accountStatus) {

    public Map<String, Object> asResponseMap() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        response.put("student_number", studentNumber);
        response.put("student_name", studentName);
        response.put("gender", gender);
        response.put("major_id", majorId);
        response.put("major_code", majorCode);
        response.put("major_name", majorName);
        response.put("account_status", accountStatus);
        return response;
    }
}
