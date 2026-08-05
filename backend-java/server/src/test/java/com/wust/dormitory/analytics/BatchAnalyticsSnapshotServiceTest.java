package com.wust.dormitory.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.common.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchAnalyticsSnapshotServiceTest {
    private NamedParameterJdbcTemplate jdbc;
    private BatchAnalyticsSnapshotService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        service = new BatchAnalyticsSnapshotService(jdbc, new ObjectMapper());
    }

    @Test
    void rejectsSnapshotBeforeBatchIsFinished() {
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of(Map.of(
                "id", 9L,
                "batch_status", "CLOSED")));

        assertThrows(BusinessException.class, () -> service.snapshot(9L));
        verify(jdbc, never()).update(anyString(), anyMap());
    }

    @Test
    void returnsExistingImmutableSnapshotWithoutRecalculation() {
        Map<String, Object> existing = Map.of(
                "id", 1L,
                "batch_id", 9L,
                "metric_version", BatchAnalyticsSnapshotService.METRIC_VERSION,
                "metrics_json", "{\"participantCount\":100}",
                "dimensions_json", "{\"sourceBasis\":\"BATCH_FINAL_RESULT\"}",
                "source_basis", "BATCH_FINAL_RESULT",
                "data_updated_at", LocalDateTime.of(2026, 8, 1, 20, 0),
                "immutable", 1,
                "created_at", LocalDateTime.of(2026, 8, 1, 20, 0));
        when(jdbc.queryForList(anyString(), anyMap()))
                .thenReturn(List.of(Map.of("id", 9L, "batch_status", "FINISHED")))
                .thenReturn(List.of(existing));

        Map<String, Object> result = service.snapshot(9L);

        assertEquals(1, result.get("immutable"));
        assertEquals(Map.of("participantCount", 100), result.get("metrics"));
        verify(jdbc, never()).update(anyString(), anyMap());
    }

    @Test
    void exposesAllRequiredMetricDefinitionsWithVersionMetadata() {
        List<MetricDefinition> definitions = service.definitions();

        assertEquals(17, definitions.size());
        definitions.forEach(definition -> {
            assertEquals(BatchAnalyticsSnapshotService.METRIC_VERSION, definition.metricVersion());
            assertEquals("BATCH_FINAL_RESULT", definition.sourceBasis());
        });
    }
}
