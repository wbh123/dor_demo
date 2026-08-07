package com.wust.dormitory.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.analytics.mapper.BatchAnalyticsSnapshotMapper;
import com.wust.dormitory.common.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchAnalyticsSnapshotServiceTest {
    private BatchAnalyticsSnapshotMapper mapper;
    private BatchAnalyticsSnapshotService service;

    @BeforeEach
    void setUp() {
        mapper = mock(BatchAnalyticsSnapshotMapper.class);
        service = new BatchAnalyticsSnapshotService(mapper, new ObjectMapper());
    }

    @Test
    void rejectsSnapshotBeforeBatchIsFinished() {
        when(mapper.findBatch(9L)).thenReturn(Map.of(
                "id", 9L,
                "batch_status", "CLOSED"));

        assertThrows(BusinessException.class, () -> service.snapshot(9L));
        verify(mapper, never()).insertStudentFacts(anyLong(), org.mockito.ArgumentMatchers.any());
        verify(mapper, never()).insertSnapshot(
                anyLong(), anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.any());
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
        when(mapper.findBatch(9L)).thenReturn(Map.of("id", 9L, "batch_status", "FINISHED"));
        when(mapper.findSnapshot(9L, BatchAnalyticsSnapshotService.METRIC_VERSION)).thenReturn(existing);

        Map<String, Object> result = service.snapshot(9L);

        assertEquals(1, result.get("immutable"));
        assertEquals(Map.of("participantCount", 100), result.get("metrics"));
        verify(mapper, never()).insertStudentFacts(anyLong(), org.mockito.ArgumentMatchers.any());
        verify(mapper, never()).insertSnapshot(
                anyLong(), anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.any());
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
