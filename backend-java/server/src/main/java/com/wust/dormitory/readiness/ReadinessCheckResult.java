package com.wust.dormitory.readiness;

import java.time.Instant;
import java.util.Map;

public record ReadinessCheckResult(
        String code,
        String category,
        String title,
        ReadinessSeverity severity,
        boolean blocking,
        String status,
        String summary,
        Map<String, Object> evidence,
        String suggestedAction,
        String actionRoute,
        Instant checkedAt) {

    public ReadinessCheckResult {
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
    }

    public static ReadinessCheckResult pass(
            String code, String category, String title, String summary, Instant checkedAt) {
        return of(code, category, title, ReadinessSeverity.PASS, false, "PASSED", summary,
                Map.of(), null, null, checkedAt);
    }

    public static ReadinessCheckResult info(
            String code, String category, String title, String summary, Instant checkedAt) {
        return of(code, category, title, ReadinessSeverity.INFO, false, "INFO", summary,
                Map.of(), null, null, checkedAt);
    }

    public static ReadinessCheckResult warning(
            String code, String category, String title, String summary,
            String suggestedAction, String actionRoute, Instant checkedAt) {
        return of(code, category, title, ReadinessSeverity.WARNING, false, "ATTENTION", summary,
                Map.of(), suggestedAction, actionRoute, checkedAt);
    }

    public static ReadinessCheckResult error(
            String code, String category, String title, String summary, boolean blocking,
            String suggestedAction, String actionRoute, Instant checkedAt) {
        return of(code, category, title, ReadinessSeverity.ERROR, blocking, "FAILED", summary,
                Map.of(), suggestedAction, actionRoute, checkedAt);
    }

    public static ReadinessCheckResult of(
            String code, String category, String title, ReadinessSeverity severity,
            boolean blocking, String status, String summary, Map<String, Object> evidence,
            String suggestedAction, String actionRoute, Instant checkedAt) {
        return new ReadinessCheckResult(code, category, title, severity, blocking, status, summary,
                evidence, suggestedAction, actionRoute, checkedAt);
    }
}
