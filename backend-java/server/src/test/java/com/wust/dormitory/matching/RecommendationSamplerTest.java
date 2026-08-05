package com.wust.dormitory.matching;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationSamplerTest {
    private static final List<RecommendationSampler.Candidate<String>> CANDIDATES = List.of(
            new RecommendationSampler.Candidate<>("room-1", 0.20d, "001"),
            new RecommendationSampler.Candidate<>("room-2", 0.90d, "002"),
            new RecommendationSampler.Candidate<>("room-3", 0.90d, "003"));

    @Test
    void bestMatchUsesScoreThenStableBusinessOrder() {
        assertEquals("room-2", RecommendationSampler.bestMatch(CANDIDATES).value());
    }

    @Test
    void trueRandomCanReachEveryLegalCandidate() {
        var selected = LongStream.range(0, 500)
                .mapToObj(seed -> RecommendationSampler.trueRandom(CANDIDATES, new SplittableRandom(seed)).value())
                .distinct()
                .toList();

        assertEquals(3, selected.size());
    }

    @Test
    void weightedRandomPrefersHighScoresWithoutRemovingLowScoreCandidates() {
        long low = LongStream.range(0, 20_000)
                .mapToObj(seed -> RecommendationSampler.weightedRandom(
                        CANDIDATES,
                        new SplittableRandom(seed),
                        0.05d,
                        0.20d).value())
                .filter("room-1"::equals)
                .count();
        long high = LongStream.range(0, 20_000)
                .mapToObj(seed -> RecommendationSampler.weightedRandom(
                        CANDIDATES,
                        new SplittableRandom(seed),
                        0.05d,
                        0.20d).value())
                .filter("room-2"::equals)
                .count();

        assertTrue(low > 0, "最低权重候选仍应保持非零概率");
        assertTrue(high > low * 3, "高匹配分候选应明显更容易被抽中");
    }
}
