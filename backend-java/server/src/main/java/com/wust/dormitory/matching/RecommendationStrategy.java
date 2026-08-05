package com.wust.dormitory.matching;

import com.wust.dormitory.subscription.FeatureCodes;

public enum RecommendationStrategy {
    BEST_MATCH("最匹配", FeatureCodes.P2_ROOM_RECOMMENDATION),
    TRUE_RANDOM("随机看看", FeatureCodes.P1_RANDOM_RECOMMENDATION),
    MATCH_WEIGHTED_RANDOM("按匹配度随机", FeatureCodes.P2_ROOM_RECOMMENDATION);

    private final String displayName;
    private final String requiredFeatureCode;

    RecommendationStrategy(String displayName, String requiredFeatureCode) {
        this.displayName = displayName;
        this.requiredFeatureCode = requiredFeatureCode;
    }

    public String displayName() {
        return displayName;
    }

    public String requiredFeatureCode() {
        return requiredFeatureCode;
    }
}
