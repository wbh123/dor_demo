package com.wust.dormitory.selection;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.subscription.AccessMode;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchSelectionModeGuardTest {
    private NamedParameterJdbcTemplate jdbc;
    private FeatureAccessService featureAccessService;
    private BatchSelectionModeGuard guard;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        featureAccessService = mock(FeatureAccessService.class);
        guard = new BatchSelectionModeGuard(jdbc, featureAccessService);
    }

    @Test
    void normalizeUsesBedAsTheSafeDefaultAndAcceptsRoomCaseInsensitively() {
        assertThat(guard.normalize(null)).isEqualTo(BatchSelectionModeGuard.BED);
        assertThat(guard.normalize(" room ")).isEqualTo(BatchSelectionModeGuard.ROOM);
    }

    @Test
    void normalizeRejectsUnknownModesWithStableErrorCode() {
        assertThatThrownBy(() -> guard.normalize("RANDOM"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("BATCH_SELECTION_MODE_INVALID"));
    }

    @Test
    void newBedBatchRequiresTheBedSelectionEntitlement() {
        guard.requireModeAvailableForNewBatch("BED");

        verify(featureAccessService).require(
                FeatureCodes.P2_BED_SELECTION_MODE,
                AccessMode.START_NEW,
                null);
    }

    @Test
    void newRoomBatchDoesNotRequireTheBedSelectionEntitlement() {
        guard.requireModeAvailableForNewBatch("ROOM");

        verify(featureAccessService, never()).require(
                FeatureCodes.P2_BED_SELECTION_MODE,
                AccessMode.START_NEW,
                null);
    }

    @Test
    void existingBedBatchUsesContinuationAccessMode() {
        when(jdbc.queryForList(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.<Map<String, ?>>any(),
                org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(List.of("BED"));

        guard.requireBedMode(91L);

        verify(featureAccessService).require(
                FeatureCodes.P2_BED_SELECTION_MODE,
                AccessMode.CONTINUE_EXISTING_BATCH,
                91L);
    }

    @Test
    void roomOperationIsRejectedForBedMode() {
        when(jdbc.queryForList(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.<Map<String, ?>>any(),
                org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(List.of("BED"));

        assertThatThrownBy(() -> guard.requireRoomMode(91L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("BATCH_SELECTION_MODE_MISMATCH");
                    assertThat(exception.getStatus().value()).isEqualTo(409);
                });
    }

    @Test
    void missingBatchIsReportedAsNotFound() {
        when(jdbc.queryForList(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.<Map<String, ?>>any(),
                org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> guard.mode(404L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("BATCH_NOT_FOUND");
                    assertThat(exception.getStatus().value()).isEqualTo(404);
                });
    }
}
