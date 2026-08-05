package com.wust.dormitory.student;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.matching.RecommendationStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecommendationIdempotencyServiceTest {
    @Test
    void normalizesStableClientRequestId() {
        assertEquals(
                "student-page:01J5A8RZ1Q9N7M4X2C6B8D0F3H",
                RecommendationIdempotencyService.normalizeClientRequestId(
                        " student-page:01J5A8RZ1Q9N7M4X2C6B8D0F3H "));
    }

    @Test
    void rejectsUnsafeOrBlankClientRequestId() {
        assertThrows(BusinessException.class,
                () -> RecommendationIdempotencyService.normalizeClientRequestId(""));
        assertThrows(BusinessException.class,
                () -> RecommendationIdempotencyService.normalizeClientRequestId("包含空格和中文"));
    }

    @Test
    void sameRequestVersionCannotChangeStrategy() {
        assertThrows(BusinessException.class,
                () -> RecommendationIdempotencyService.requireSameStrategy(
                        RecommendationStrategy.TRUE_RANDOM.name(),
                        RecommendationStrategy.BEST_MATCH));
    }
}
