package com.wust.dormitory.admin;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.residency.BatchRoomLockService;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.subscription.EntitlementSnapshotService;
import com.wust.dormitory.subscription.FeatureAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BatchLifecycleServiceTest {
    private NamedParameterJdbcTemplate jdbc;
    private BatchScopeService batchScopeService;
    private BatchRoomLockService roomLockService;
    private FeatureAccessService featureAccessService;
    private EntitlementSnapshotService entitlementSnapshotService;
    private AuditService auditService;
    private BatchLifecycleService service;
    private CurrentUser operator;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        batchScopeService = mock(BatchScopeService.class);
        roomLockService = mock(BatchRoomLockService.class);
        featureAccessService = mock(FeatureAccessService.class);
        entitlementSnapshotService = mock(EntitlementSnapshotService.class);
        auditService = mock(AuditService.class);
        service = new BatchLifecycleService(
                jdbc, batchScopeService, roomLockService,
                featureAccessService, entitlementSnapshotService, auditService);
        operator = new CurrentUser(7L, null, "admin", "管理员", "ADMIN");
    }

    @Test
    void publishingValidatesScopeBeforeRoomPreflightAndLocks() {
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of(Map.of(
                "id", 12L,
                "batch_status", "DRAFT",
                "selection_mode", "ROOM",
                "separate_student_categories", 0)));

        service.changeStatus(12L, "PUBLISHED", operator);

        InOrder order = inOrder(batchScopeService, roomLockService, entitlementSnapshotService, jdbc);
        order.verify(batchScopeService).requireReady(12L);
        order.verify(roomLockService).requirePublishable(12L);
        order.verify(roomLockService).acquire(12L);
        order.verify(entitlementSnapshotService).captureForBatch(12L);
        order.verify(jdbc).update(anyString(), anyMap());
        verify(auditService).success(
                org.mockito.ArgumentMatchers.eq(operator),
                org.mockito.ArgumentMatchers.eq("BATCH_STATUS_CHANGE"),
                org.mockito.ArgumentMatchers.eq("SELECTION_BATCH"),
                org.mockito.ArgumentMatchers.eq(12L),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void repeatedPublishReturnsWithoutReacquiringLocksOrChangingStatus() {
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of(Map.of(
                "id", 12L,
                "batch_status", "PUBLISHED",
                "selection_mode", "ROOM",
                "separate_student_categories", 0)));

        service.changeStatus(12L, "PUBLISHED", operator);

        verifyNoInteractions(
                batchScopeService, roomLockService, featureAccessService,
                entitlementSnapshotService, auditService);
    }

    @Test
    void finishingRecordsImmutableCompletionTimestamp() {
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of(Map.of(
                "id", 12L,
                "batch_status", "CLOSED",
                "selection_mode", "ROOM",
                "separate_student_categories", 0)));

        service.changeStatus(12L, "FINISHED", operator);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), anyMap());
        assertTrue(sql.getValue().contains("finished_at=CASE"));
        assertTrue(sql.getValue().contains("COALESCE(finished_at,CURRENT_TIMESTAMP(3))"));
    }
}
