package com.wust.dormitory.readiness;

import java.time.Instant;
import java.util.List;

public record SystemReadinessReport(
        ReadinessOverallStatus overallStatus,
        Instant checkedAt,
        SystemReadinessSummary summary,
        List<String> categories,
        List<ReadinessCheckResult> checks) {
}
