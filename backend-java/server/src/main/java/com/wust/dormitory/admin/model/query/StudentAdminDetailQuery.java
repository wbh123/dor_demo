package com.wust.dormitory.admin.model.query;

public record StudentAdminDetailQuery(
        String keywordPattern,
        String gender,
        Long majorId,
        String studentCategory,
        String enrollmentSource,
        int limit,
        int offset) {
}
