package com.wust.dormitory.matching;

import com.wust.dormitory.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BatchRecommendationPolicyServiceTest {
    @Test
    void defaultStrategyMustBelongToAllowedStrategies() {
        assertThrows(IllegalArgumentException.class, () ->
                new BatchRecommendationPolicyService.Policy(
                        List.of(RecommendationStrategy.TRUE_RANDOM),
                        RecommendationStrategy.BEST_MATCH,
                        0.05d,
                        0.20d));
    }

    @Test
    void batchRejectsClientStrategyOutsidePublishedPolicy() {
        var policy = new BatchRecommendationPolicyService.Policy(
                List.of(RecommendationStrategy.BEST_MATCH),
                RecommendationStrategy.BEST_MATCH,
                0.05d,
                0.20d);

        assertThrows(BusinessException.class,
                () -> policy.requireAllowed(RecommendationStrategy.TRUE_RANDOM));
    }

    @Test
    void weightedParametersRemainVersionedWithPolicy() {
        var policy = new BatchRecommendationPolicyService.Policy(
                List.of(
                        RecommendationStrategy.BEST_MATCH,
                        RecommendationStrategy.TRUE_RANDOM,
                        RecommendationStrategy.MATCH_WEIGHTED_RANDOM),
                RecommendationStrategy.MATCH_WEIGHTED_RANDOM,
                0.08d,
                0.35d);

        assertEquals(0.08d, policy.baseWeight());
        assertEquals(0.35d, policy.temperature());
    }
}
