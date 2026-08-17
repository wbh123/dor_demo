package com.wust.dormitory.readiness;

public record SystemReadinessSummary(
        int total,
        int passed,
        int info,
        int warnings,
        int errors,
        int blocking) {
}
