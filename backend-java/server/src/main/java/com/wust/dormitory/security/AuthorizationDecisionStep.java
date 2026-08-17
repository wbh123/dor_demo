package com.wust.dormitory.security;

public record AuthorizationDecisionStep(
        String code,
        String title,
        AuthorizationStepResult result,
        String required,
        String actual,
        String reason,
        String source,
        String relatedId) {
}
