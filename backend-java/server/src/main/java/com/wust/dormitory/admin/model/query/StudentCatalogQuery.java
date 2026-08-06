package com.wust.dormitory.admin.model.query;

public record StudentCatalogQuery(
        String keywordPattern,
        String gender,
        Long majorId,
        int limit,
        int offset) {
}
