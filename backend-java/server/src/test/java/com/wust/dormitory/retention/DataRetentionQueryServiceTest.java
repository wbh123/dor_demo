package com.wust.dormitory.retention;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import com.wust.dormitory.subscription.QuotaCodes;
import com.wust.dormitory.subscription.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataRetentionQueryServiceTest {
    private NamedParameterJdbcTemplate jdbc;
    private FeatureAccessService featureAccessService;
    private SubscriptionService subscriptionService;
    private DataRetentionQueryService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        featureAccessService = mock(FeatureAccessService.class);
        subscriptionService = mock(SubscriptionService.class);
        service = new DataRetentionQueryService(
                jdbc, featureAccessService, subscriptionService,
                new ObjectMapper().findAndRegisterModules());
        when(subscriptionService.currentSubscription()).thenReturn(new SubscriptionService.CurrentSubscription(
                1L, 1L, 1, 8L, "STANDARD", "标准版", 1,
                "LONG_TERM", "ACTIVE", null,
                LocalDateTime.of(2026, 1, 1, 0, 0), null, false));
        when(subscriptionService.quotasForPlanRevision(8L)).thenReturn(Map.of(
                QuotaCodes.DATA_RETENTION_DAYS, 365L,
                QuotaCodes.AUDIT_RETENTION_DAYS, 730L));
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), any(Class.class)))
                .thenReturn(0L);
    }

    @Test
    void policyExposesQuotasAndNeverEnablesDeletion() {
        Map<String, Object> policy = service.policy();

        assertEquals(365L, policy.get("dataRetentionDays"));
        assertEquals(730L, policy.get("auditRetentionDays"));
        assertFalse((Boolean) policy.get("executionEnabled"));
        verify(featureAccessService).require(FeatureCodes.P3_DATA_RETENTION_QUERY);
    }

    @Test
    void preflightRequiresReasonAndStoresValidJsonSnapshots() {
        CurrentUser admin = new CurrentUser(1L, null, "admin", "管理员", "ADMIN");
        assertThrows(BusinessException.class, () -> service.preflight(admin, ""));

        Map<String, Object> result = service.preflight(admin, "年度数据治理检查");

        assertFalse((Boolean) result.get("executionAllowed"));
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(anyString(), parameters.capture());
        String policyJson = String.valueOf(parameters.getValue().getValue("policy"));
        String simulationJson = String.valueOf(parameters.getValue().getValue("simulation"));
        assertTrue(policyJson.startsWith("{"));
        assertTrue(simulationJson.startsWith("{"));
        assertEquals("年度数据治理检查", parameters.getValue().getValue("reason"));
    }

    @Test
    void simulationKeepsAllProtectedBusinessReasons() {
        Map<String, Object> simulation = service.simulate();
        Map<?, ?> counts = (Map<?, ?>) simulation.get("protectedCounts");

        assertTrue(counts.containsKey(DataRetentionQueryService.CURRENT_STUDENT));
        assertTrue(counts.containsKey(DataRetentionQueryService.ACTIVE_RESIDENCY));
        assertTrue(counts.containsKey(DataRetentionQueryService.ACTIVE_BATCH));
        assertTrue(counts.containsKey(DataRetentionQueryService.PENDING_ROOM_CHANGE));
        assertTrue(counts.containsKey(DataRetentionQueryService.PENDING_EXCHANGE));
        assertTrue(counts.containsKey(DataRetentionQueryService.PENDING_WAITLIST));
        assertTrue(counts.containsKey(DataRetentionQueryService.ACTIVE_ENTITLEMENT));
        assertTrue(counts.containsKey(DataRetentionQueryService.PENDING_EXPORT));
        assertTrue(counts.containsKey(DataRetentionQueryService.LEGAL_AUDIT_HOLD));
    }
}
