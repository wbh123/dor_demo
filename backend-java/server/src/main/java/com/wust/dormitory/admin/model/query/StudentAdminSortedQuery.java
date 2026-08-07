package com.wust.dormitory.admin.model.query;

public record StudentAdminSortedQuery(
        String keywordPattern,
        String gender,
        Long majorId,
        String studentCategory,
        String enrollmentSource,
        String sortField,
        String sortDirection,
        int limit,
        int offset) {
}
