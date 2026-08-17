package com.wust.dormitory.readiness;

import com.wust.dormitory.admin.BatchRuleTemplateService;
import com.wust.dormitory.admin.BatchScopeService;
import com.wust.dormitory.matching.MatchingSchemeService;
import com.wust.dormitory.readiness.mapper.SystemReadinessMapper;
import com.wust.dormitory.residency.BatchRoomLockService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BatchReadinessCheckerTest {
    private static final ReadinessContext CONTEXT = new ReadinessContext(Instant.parse("2026-08-17T05:00:00Z"));

    @Test
    void exposesPendingParticipantCountForRemainingCapacityChecks() {
        assertDoesNotThrow(() -> SystemReadinessMapper.class.getDeclaredMethod("pendingParticipantCount", long.class));
    }

    @Test
    void noActiveBatchIsInformational() {
        SystemReadinessMapper mapper = mock(SystemReadinessMapper.class);
        BatchScopeService scope = mock(BatchScopeService.class);
        BatchRuleTemplateService rules = mock(BatchRuleTemplateService.class);
        BatchRoomLockService preflight = mock(BatchRoomLockService.class);
        MatchingSchemeService matching = mock(MatchingSchemeService.class);
        when(mapper.activeBatches()).thenReturn(List.of());

        List<ReadinessCheckResult> results = new BatchReadinessChecker(mapper, scope, rules, preflight, matching)
                .check(CONTEXT);

        assertEquals(1, results.size());
        assertEquals(ReadinessSeverity.INFO, results.getFirst().severity());
    }

    @Test
    void blocksWhenPendingParticipantsExceedRemainingCapacityAndReusesExistingValidators() {
        SystemReadinessMapper mapper = mock(SystemReadinessMapper.class);
        BatchScopeService scope = mock(BatchScopeService.class);
        BatchRuleTemplateService rules = mock(BatchRuleTemplateService.class);
        BatchRoomLockService preflight = mock(BatchRoomLockService.class);
        MatchingSchemeService matching = mock(MatchingSchemeService.class);
        when(mapper.activeBatches()).thenReturn(List.of(activeBatch(42L, "2026 新生第一批", "PUBLISHED", 9L)));
        when(mapper.participantCount(42L)).thenReturn(500L);
        when(mapper.pendingParticipantCount(42L)).thenReturn(500L);
        when(preflight.preview(42L)).thenReturn(Map.of(
                "roomCount", 80,
                "availableCapacity", 480,
                "publishable", true,
                "studentConflictCount", 0));

        ReadinessCheckResult result = new BatchReadinessChecker(mapper, scope, rules, preflight, matching)
                .check(CONTEXT).getFirst();

        assertTrue(result.blocking());
        assertEquals(ReadinessSeverity.ERROR, result.severity());
        assertEquals(Boolean.TRUE, result.evidence().get("capacityShortage"));
        assertEquals(Boolean.TRUE, result.evidence().get("scopeReady"));
        assertEquals(Boolean.TRUE, result.evidence().get("ruleRevisionValid"));
        verify(scope).requireReady(42L);
        verify(rules).resolveForBatch(9L);
        verify(preflight).preview(42L);
        verify(matching).policyForBatch(42L);
    }

    @Test
    void openBatchUsesOnlyStillUnassignedParticipantsForRemainingCapacity() {
        SystemReadinessMapper mapper = mock(SystemReadinessMapper.class);
        BatchScopeService scope = mock(BatchScopeService.class);
        BatchRuleTemplateService rules = mock(BatchRuleTemplateService.class);
        BatchRoomLockService preflight = mock(BatchRoomLockService.class);
        MatchingSchemeService matching = mock(MatchingSchemeService.class);
        when(mapper.activeBatches()).thenReturn(List.of(activeBatch(44L, "开放中批次", "OPEN", 9L)));
        when(mapper.participantCount(44L)).thenReturn(500L);
        when(mapper.pendingParticipantCount(44L)).thenReturn(400L);
        when(preflight.preview(44L)).thenReturn(Map.of(
                "roomCount", 80,
                "availableCapacity", 400,
                "publishable", true,
                "studentConflictCount", 0));

        ReadinessCheckResult result = new BatchReadinessChecker(mapper, scope, rules, preflight, matching)
                .check(CONTEXT).getFirst();

        assertFalse(result.blocking());
        assertEquals(ReadinessSeverity.PASS, result.severity());
        assertEquals(500L, result.evidence().get("participantCount"));
        assertEquals(400L, result.evidence().get("pendingParticipantCount"));
        assertEquals(Boolean.FALSE, result.evidence().get("capacityShortage"));
    }

    @Test
    void missingBoundRuleRevisionBlocksWithoutDefaultFallback() {
        SystemReadinessMapper mapper = mock(SystemReadinessMapper.class);
        BatchScopeService scope = mock(BatchScopeService.class);
        BatchRuleTemplateService rules = mock(BatchRuleTemplateService.class);
        BatchRoomLockService preflight = mock(BatchRoomLockService.class);
        MatchingSchemeService matching = mock(MatchingSchemeService.class);
        when(mapper.activeBatches()).thenReturn(List.of(activeBatch(43L, "缺规则批次", "PUBLISHED", null)));
        when(mapper.participantCount(43L)).thenReturn(10L);
        when(mapper.pendingParticipantCount(43L)).thenReturn(10L);
        when(preflight.preview(43L)).thenReturn(Map.of(
                "roomCount", 4,
                "availableCapacity", 20,
                "publishable", true,
                "studentConflictCount", 0));

        ReadinessCheckResult result = new BatchReadinessChecker(mapper, scope, rules, preflight, matching)
                .check(CONTEXT).getFirst();

        assertTrue(result.blocking());
        assertEquals(Boolean.FALSE, result.evidence().get("ruleRevisionBound"));
        assertEquals("RULE_TEMPLATE_NOT_BOUND", result.evidence().get("ruleCheckError"));
        verifyNoInteractions(rules);
    }

    @Test
    void blocksWhenExistingPublishPreflightRejectsBatch() {
        SystemReadinessMapper mapper = mock(SystemReadinessMapper.class);
        BatchScopeService scope = mock(BatchScopeService.class);
        BatchRuleTemplateService rules = mock(BatchRuleTemplateService.class);
        BatchRoomLockService preflight = mock(BatchRoomLockService.class);
        MatchingSchemeService matching = mock(MatchingSchemeService.class);
        when(mapper.activeBatches()).thenReturn(List.of(activeBatch(7L, "冲突批次", "PAUSED", 2L)));
        when(mapper.participantCount(7L)).thenReturn(100L);
        when(mapper.pendingParticipantCount(7L)).thenReturn(100L);
        when(preflight.preview(7L)).thenReturn(Map.of(
                "roomCount", 20,
                "availableCapacity", 120,
                "publishable", false,
                "studentConflictCount", 3));

        ReadinessCheckResult result = new BatchReadinessChecker(mapper, scope, rules, preflight, matching)
                .check(CONTEXT).getFirst();

        assertTrue(result.blocking());
        assertEquals(Boolean.FALSE, result.evidence().get("publishable"));
        assertEquals(3L, result.evidence().get("studentConflictCount"));
    }

    private Map<String, Object> activeBatch(long id, String name, String status, Long ruleTemplateId) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", id);
        result.put("batchName", name);
        result.put("batchStatus", status);
        result.put("ruleTemplateId", ruleTemplateId);
        return result;
    }
}
