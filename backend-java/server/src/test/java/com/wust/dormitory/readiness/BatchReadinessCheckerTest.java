package com.wust.dormitory.readiness;

import com.wust.dormitory.admin.BatchRuleTemplateService;
import com.wust.dormitory.admin.BatchScopeService;
import com.wust.dormitory.matching.MatchingSchemeService;
import com.wust.dormitory.readiness.mapper.SystemReadinessMapper;
import com.wust.dormitory.residency.BatchRoomLockService;
import com.wust.dormitory.selection.BatchSelectionModeGuard;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
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
        when(mapper.activeBatches()).thenReturn(List.of());

        List<ReadinessCheckResult> results = checker(mapper).check(CONTEXT);

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
        BatchSelectionModeGuard modeGuard = mock(BatchSelectionModeGuard.class);
        when(mapper.activeBatches()).thenReturn(List.of(activeBatch(42L, "2026 新生第一批", "PUBLISHED", 9L)));
        when(mapper.participantCount(42L)).thenReturn(500L);
        when(mapper.pendingParticipantCount(42L)).thenReturn(500L);
        when(preflight.preview(42L)).thenReturn(Map.of(
                "roomCount", 80,
                "availableCapacity", 480,
                "publishable", true,
                "studentConflictCount", 0));

        ReadinessCheckResult result = new BatchReadinessChecker(mapper, scope, rules, preflight, matching, modeGuard)
                .check(CONTEXT).getFirst();

        assertTrue(result.blocking());
        assertEquals(ReadinessSeverity.ERROR, result.severity());
        assertEquals(Boolean.TRUE, result.evidence().get("capacityShortage"));
        assertEquals(Boolean.TRUE, result.evidence().get("scopeReady"));
        assertEquals(Boolean.TRUE, result.evidence().get("ruleRevisionValid"));
        assertEquals(Boolean.TRUE, result.evidence().get("selectionModeAvailable"));
        verify(scope).requireReady(42L);
        verify(rules).resolveForBatch(9L);
        verify(preflight).preview(42L);
        verify(matching).policyForBatch(42L);
        verify(modeGuard).requireBedModeForPublish(42L);
    }

    @Test
    void blocksWhenExistingSelectionModeEntitlementIsUnavailable() {
        SystemReadinessMapper mapper = mock(SystemReadinessMapper.class);
        BatchScopeService scope = mock(BatchScopeService.class);
        BatchRuleTemplateService rules = mock(BatchRuleTemplateService.class);
        BatchRoomLockService preflight = mock(BatchRoomLockService.class);
        MatchingSchemeService matching = mock(MatchingSchemeService.class);
        BatchSelectionModeGuard modeGuard = mock(BatchSelectionModeGuard.class);
        when(mapper.activeBatches()).thenReturn(List.of(activeBatch(45L, "床位模式批次", "PUBLISHED", 9L)));
        when(mapper.participantCount(45L)).thenReturn(20L);
        when(mapper.pendingParticipantCount(45L)).thenReturn(20L);
        when(preflight.preview(45L)).thenReturn(Map.of(
                "roomCount", 5,
                "availableCapacity", 20,
                "publishable", true,
                "studentConflictCount", 0));
        doThrow(new IllegalStateException("bed mode feature revoked"))
                .when(modeGuard).requireBedModeForPublish(45L);

        ReadinessCheckResult result = new BatchReadinessChecker(mapper, scope, rules, preflight, matching, modeGuard)
                .check(CONTEXT).getFirst();

        assertTrue(result.blocking());
        assertEquals(Boolean.FALSE, result.evidence().get("selectionModeAvailable"));
        assertEquals("IllegalStateException", result.evidence().get("selectionModeCheckError"));
    }

    @Test
    void openBatchUsesOnlyStillUnassignedParticipantsForRemainingCapacity() {
        SystemReadinessMapper mapper = mock(SystemReadinessMapper.class);
        BatchRoomLockService preflight = mock(BatchRoomLockService.class);
        when(mapper.activeBatches()).thenReturn(List.of(activeBatch(44L, "开放中批次", "OPEN", 9L)));
        when(mapper.participantCount(44L)).thenReturn(500L);
        when(mapper.pendingParticipantCount(44L)).thenReturn(400L);
        when(preflight.preview(44L)).thenReturn(Map.of(
                "roomCount", 80,
                "availableCapacity", 400,
                "publishable", true,
                "studentConflictCount", 0));

        ReadinessCheckResult result = checker(mapper, preflight).check(CONTEXT).getFirst();

        assertFalse(result.blocking());
        assertEquals(ReadinessSeverity.PASS, result.severity());
        assertEquals(500L, result.evidence().get("participantCount"));
        assertEquals(400L, result.evidence().get("pendingParticipantCount"));
        assertEquals(Boolean.FALSE, result.evidence().get("capacityShortage"));
    }

    @Test
    void missingBoundRuleRevisionBlocksWithoutDefaultFallback() {
        SystemReadinessMapper mapper = mock(SystemReadinessMapper.class);
        BatchRuleTemplateService rules = mock(BatchRuleTemplateService.class);
        BatchRoomLockService preflight = mock(BatchRoomLockService.class);
        when(mapper.activeBatches()).thenReturn(List.of(activeBatch(43L, "缺规则批次", "PUBLISHED", null)));
        when(mapper.participantCount(43L)).thenReturn(10L);
        when(mapper.pendingParticipantCount(43L)).thenReturn(10L);
        when(preflight.preview(43L)).thenReturn(Map.of(
                "roomCount", 4,
                "availableCapacity", 20,
                "publishable", true,
                "studentConflictCount", 0));

        ReadinessCheckResult result = new BatchReadinessChecker(
                mapper, mock(BatchScopeService.class), rules, preflight,
                mock(MatchingSchemeService.class), mock(BatchSelectionModeGuard.class))
                .check(CONTEXT).getFirst();

        assertTrue(result.blocking());
        assertEquals(Boolean.FALSE, result.evidence().get("ruleRevisionBound"));
        assertEquals("RULE_TEMPLATE_NOT_BOUND", result.evidence().get("ruleCheckError"));
        verifyNoInteractions(rules);
    }

    @Test
    void blocksWhenExistingPublishPreflightRejectsBatch() {
        SystemReadinessMapper mapper = mock(SystemReadinessMapper.class);
        BatchRoomLockService preflight = mock(BatchRoomLockService.class);
        when(mapper.activeBatches()).thenReturn(List.of(activeBatch(7L, "冲突批次", "PAUSED", 2L)));
        when(mapper.participantCount(7L)).thenReturn(100L);
        when(mapper.pendingParticipantCount(7L)).thenReturn(100L);
        when(preflight.preview(7L)).thenReturn(Map.of(
                "roomCount", 20,
                "availableCapacity", 120,
                "publishable", false,
                "studentConflictCount", 3));

        ReadinessCheckResult result = checker(mapper, preflight).check(CONTEXT).getFirst();

        assertTrue(result.blocking());
        assertEquals(Boolean.FALSE, result.evidence().get("publishable"));
        assertEquals(3L, result.evidence().get("studentConflictCount"));
    }

    private BatchReadinessChecker checker(SystemReadinessMapper mapper) {
        return checker(mapper, mock(BatchRoomLockService.class));
    }

    private BatchReadinessChecker checker(SystemReadinessMapper mapper, BatchRoomLockService preflight) {
        return new BatchReadinessChecker(
                mapper,
                mock(BatchScopeService.class),
                mock(BatchRuleTemplateService.class),
                preflight,
                mock(MatchingSchemeService.class),
                mock(BatchSelectionModeGuard.class));
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
