package com.wust.dormitory.admin.model.persistence;

import java.util.LinkedHashMap;
import java.util.Map;

public record StudentAdminDetailRow(
        long id,
        String studentNumber,
        String studentName,
        String gender,
        String nationalityCode,
        String studentCategory,
        String enrollmentSource,
        String phoneNumber,
        String degreeLevel,
        Integer gradeYear,
        long majorId,
        String majorCode,
        String majorName,
        String accountStatus,
        boolean currentlyResident,
        Long currentResidencyId,
        String currentBuildingName,
        String currentRoomNumber,
        String currentBedCode,
        String currentBedType,
        String selectionReviewStatus,
        String declaredBedCode,
        String declaredBedType) {

    public Map<String, Object> asResponseMap() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        response.put("student_number", studentNumber);
        response.put("student_name", studentName);
        response.put("gender", gender);
        response.put("nationality_code", nationalityCode);
        response.put("student_category", studentCategory);
        response.put("enrollment_source", enrollmentSource);
        response.put("phone_number", phoneNumber);
        response.put("degree_level", degreeLevel);
        response.put("grade_year", gradeYear);
        response.put("major_id", majorId);
        response.put("major_code", majorCode);
        response.put("major_name", majorName);
        response.put("account_status", accountStatus);
        response.put("currently_resident", currentlyResident);
        response.put("current_residency_id", currentResidencyId);
        response.put("current_building_name", currentBuildingName);
        response.put("current_room_number", currentRoomNumber);
        response.put("current_bed_code", currentBedCode);
        response.put("current_bed_type", currentBedType);
        response.put("selection_review_status", selectionReviewStatus);
        response.put("declared_bed_code", declaredBedCode);
        response.put("declared_bed_type", declaredBedType);
        return response;
    }
}
