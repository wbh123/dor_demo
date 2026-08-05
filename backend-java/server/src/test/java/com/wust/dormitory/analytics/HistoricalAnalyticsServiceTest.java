package com.wust.dormitory.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HistoricalAnalyticsServiceTest {
    private NamedParameterJdbcTemplate jdbc;
    private FeatureAccessService featureAccessService;
    private HistoricalAnalyticsService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        featureAccessService = mock(FeatureAccessService.class);
        service = new HistoricalAnalyticsService(jdbc, featureAccessService, new ObjectMapper());
    }

    @Test
    void comparisonFiltersImmutableFactsAndSuppressesSmallSamples() {
        when(jdbc.queryForList(any(String.class), any(MapSqlParameterSource.class))).thenReturn(List.of(Map.ofEntries(
                Map.entry("id", 1L),
                Map.entry("batch_id", 9L),
                Map.entry("metric_version", BatchAnalyticsSnapshotService.METRIC_VERSION),
                Map.entry("metrics_json", "{\"studentTotal\":100,\"bedUtilizationRate\":0.95}"),
                Map.entry("dimensions_json", "{\"sourceBasis\":\"BATCH_FINAL_RESULT\"}"),
                Map.entry("source_basis", "BATCH_FINAL_RESULT"),
                Map.entry("data_updated_at", LocalDateTime.of(2026, 8, 1, 20, 0)),
                Map.entry("batch_code", "2026-A"),
                Map.entry("batch_name", "2026级选寝"),
                Map.entry("academic_year", 2026),
                Map.entry("sample_size", 3L),
                Map.entry("self_selection_count", 2L),
                Map.entry("team_selection_count", 1L),
                Map.entry("unified_allocation_count", 0L),
                Map.entry("unassigned_count", 0L),
                Map.entry("recommendation_adoption_count", 2L),
                Map.entry("average_match_score", 0.88D),
                Map.entry("minimum_match_score", 0.82D),
                Map.entry("room_change_count", 0L),
                Map.entry("exchange_count", 0L),
                Map.entry("waitlist_request_count", 0L),
                Map.entry("waitlist_assignment_count", 0L),
                Map.entry("manual_adjustment_student_count", 0L))));

        Map<String, Object> response = service.comparison(new HistoricalAnalyticsService.AnalyticsFilter(
                2026, null, 5L, null, "UNDERGRADUATE", "DOMESTIC", null, null, "FOUR_PERSON"));

        verify(featureAccessService).require(FeatureCodes.P3_CROSS_BATCH_COMPARISON);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), any(MapSqlParameterSource.class));
        assertTrue(sql.getValue().contains("batch_analytics_student_fact fact"));
        assertTrue(sql.getValue().contains("fact.major_id=:majorId"));
        assertEquals(3L, response.get("sampleSize"));
        assertEquals(true, response.get("preferenceDimensionsSuppressed"));
        Map<?, ?> item = (Map<?, ?>) ((List<?>) response.get("items")).getFirst();
        Map<?, ?> metrics = (Map<?, ?>) item.get("metrics");
        assertNull(metrics.get("recommendationAdoptionCount"));
        assertNull(metrics.get("averageMatchScore"));
        assertEquals(0.95D, metrics.get("bedUtilizationRate"));
    }
}
