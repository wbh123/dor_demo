package com.wust.dormitory.subscription;

/**
 * 计算程序实现、系统授权、学校开关和业务状态共同决定的最终功能状态。
 */
public final class FeatureAccessEvaluator {
    private FeatureAccessEvaluator() {
    }

    public record Input(
            boolean enabledInProgram,
            boolean systemGranted,
            boolean schoolControllable,
            boolean schoolDefaultEnabled,
            Boolean schoolSettingEnabled,
            boolean businessAllowed) {
    }

    public record State(
            boolean enabledInProgram,
            boolean systemGranted,
            boolean schoolEnabled,
            boolean effectiveEnabled,
            String unavailableReason) {
    }

    public static State evaluate(Input input) {
        boolean schoolEnabled = !input.schoolControllable()
                || (input.schoolSettingEnabled() == null
                ? input.schoolDefaultEnabled()
                : input.schoolSettingEnabled());
        boolean effective = input.enabledInProgram()
                && input.systemGranted()
                && schoolEnabled
                && input.businessAllowed();
        return new State(
                input.enabledInProgram(),
                input.systemGranted(),
                schoolEnabled,
                effective,
                reason(input, schoolEnabled));
    }

    private static String reason(Input input, boolean schoolEnabled) {
        if (!input.enabledInProgram()) {
            return "NOT_IMPLEMENTED";
        }
        if (!input.systemGranted()) {
            return "SYSTEM_NOT_GRANTED";
        }
        if (!schoolEnabled) {
            return "SCHOOL_DISABLED";
        }
        if (!input.businessAllowed()) {
            return "BUSINESS_STATE_BLOCKED";
        }
        return "ENABLED";
    }
}
