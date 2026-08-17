package com.wust.dormitory.selection;

import com.wust.dormitory.subscription.AccessMode;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BatchSelectionModeGuardContinuationTest {
    @Test
    void bedModeExistingBatchUsesContinuationEntitlementSnapshot() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        FeatureAccessService features = mock(FeatureAccessService.class);
        when(jdbc.queryForList(anyString(), anyMap(), eq(String.class))).thenReturn(List.of("BED"));

        new BatchSelectionModeGuard(jdbc, features).requireModeAvailableForExistingBatch(45L);

        verify(features).require(
                FeatureCodes.P2_BED_SELECTION_MODE,
                AccessMode.CONTINUE_EXISTING_BATCH,
                45L);
    }

    @Test
    void roomModeExistingBatchDoesNotRequireBedEntitlement() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        FeatureAccessService features = mock(FeatureAccessService.class);
        when(jdbc.queryForList(anyString(), anyMap(), eq(String.class))).thenReturn(List.of("ROOM"));

        new BatchSelectionModeGuard(jdbc, features).requireModeAvailableForExistingBatch(46L);

        verifyNoInteractions(features);
    }
}
