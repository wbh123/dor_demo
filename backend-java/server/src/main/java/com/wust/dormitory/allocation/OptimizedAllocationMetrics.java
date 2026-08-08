package com.wust.dormitory.allocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class OptimizedAllocationMetrics {
    private OptimizedAllocationMetrics() {
    }

    static Map<String, Object> calculate(
            List<OptimizedAllocationRunService.Candidate> candidates,
            int unassignedCount) {
        double average = candidates.stream()
                .mapToDouble(OptimizedAllocationRunService.Candidate::score)
                .average().orElse(0.0d);
        double minimum = candidates.stream()
                .mapToDouble(OptimizedAllocationRunService.Candidate::score)
                .min().orElse(0.0d);
        double variance = candidates.stream()
                .mapToDouble(candidate -> Math.pow(candidate.score() - average, 2))
                .average().orElse(0.0d);
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("assignedCount", candidates.size());
        metrics.put("unassignedCount", unassignedCount);
        metrics.put("averageScore", round(average));
        metrics.put("minimumScore", round(minimum));
        metrics.put("standardDeviation", round(Math.sqrt(variance)));
        metrics.put("fairness", round(average <= 0.0d ? 0.0d : Math.max(0.0d, 100.0d - Math.sqrt(variance))));
        return metrics;
    }

    private static double round(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }
}
