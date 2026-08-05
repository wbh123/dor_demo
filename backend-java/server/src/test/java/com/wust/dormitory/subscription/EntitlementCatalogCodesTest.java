package com.wust.dormitory.subscription;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntitlementCatalogCodesTest {
    @Test
    void exposesConcurrencyAndOptimizedAllocationFeatureCodes() {
        assertEquals("P2_CONCURRENT_SELECTION_LIMIT", FeatureCodes.P2_CONCURRENT_SELECTION_LIMIT);
        assertEquals("P2_ALLOCATION_OPTIMIZED_EXECUTE", FeatureCodes.P2_ALLOCATION_OPTIMIZED_EXECUTE);
        assertEquals("P2_ALLOCATION_LOCAL_SWAP", FeatureCodes.P2_ALLOCATION_LOCAL_SWAP);
        assertEquals("P2_FAIRNESS_COMPARISON", FeatureCodes.P2_FAIRNESS_COMPARISON);
        assertEquals("P2_ALLOCATION_EXPERIMENT_EXPORT", FeatureCodes.P2_ALLOCATION_EXPERIMENT_EXPORT);
    }

    @Test
    void exposesConcurrentSelectionQuotaCode() {
        assertEquals(
                "MAX_CONCURRENT_SELECTION_USERS",
                QuotaCodes.MAX_CONCURRENT_SELECTION_USERS);
    }
}
