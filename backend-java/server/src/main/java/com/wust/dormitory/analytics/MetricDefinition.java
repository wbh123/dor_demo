package com.wust.dormitory.analytics;

import java.time.LocalDateTime;
import java.util.Map;

public record MetricDefinition(
        String code,
        String nameZhCn,
        String nameEnUs,
        String timeRange,
        Map<String, Object> filters,
        String sourceBasis,
        LocalDateTime dataUpdatedAt,
        String metricVersion,
        String privacyNote) {

    public MetricDefinition withUpdateTime(LocalDateTime updateTime) {
        return new MetricDefinition(
                code,
                nameZhCn,
                nameEnUs,
                timeRange,
                filters,
                sourceBasis,
                updateTime,
                metricVersion,
                privacyNote);
    }
}
