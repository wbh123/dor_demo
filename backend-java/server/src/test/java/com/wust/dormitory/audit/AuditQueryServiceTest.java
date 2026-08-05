package com.wust.dormitory.audit;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditQueryServiceTest {
    private NamedParameterJdbcTemplate jdbc;
    private FeatureAccessService featureAccessService;
    private AuditQueryService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        featureAccessService = mock(FeatureAccessService.class);
        service = new AuditQueryService(jdbc, featureAccessService);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Integer.class))).thenReturn(1);
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of("id", 9L, "action_type", "STUDENT_UPDATE")));
    }

    @Test
    void buildsNamedParameterQueryForAllAdvancedFilters() {
        AuditQueryService.AuditQuery query = new AuditQueryService.AuditQuery(
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 5, 0, 0),
                7L, "ADMIN", "STUDENT", "STUDENT_UPDATE",
                "STUDENT", "88", false, "VALIDATION_ERROR",
                "request-1", "10.0.0.8", "手机号", 2, 500);

        Map<String, Object> result = service.query(query);

        assertEquals(1, result.get("total"));
        assertEquals(2, result.get("page"));
        assertEquals(200, result.get("size"));
        verify(featureAccessService).require(FeatureCodes.P2_AUDIT_ADVANCED_QUERY);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).queryForList(sql.capture(), parameters.capture());
        assertTrue(sql.getValue().contains("audit.operator_user_id=:operatorId"));
        assertTrue(sql.getValue().contains("audit.request_id=:requestId"));
        assertTrue(sql.getValue().contains("audit.ip_address=:networkAddress"));
        assertTrue(sql.getValue().contains("audit.ip_address AS network_address"));
        assertEquals(200, parameters.getValue().getValue("size"));
        assertEquals(200, parameters.getValue().getValue("offset"));
    }

    @Test
    void rejectsInvertedTimeRangeBeforeExecutingSql() {
        AuditQueryService.AuditQuery query = new AuditQueryService.AuditQuery(
                LocalDateTime.of(2026, 8, 5, 0, 0),
                LocalDateTime.of(2026, 8, 1, 0, 0),
                null, null, null, null, null, null,
                null, null, null, null, null, 1, 20);

        assertThrows(IllegalArgumentException.class, () -> service.query(query));
    }
}
