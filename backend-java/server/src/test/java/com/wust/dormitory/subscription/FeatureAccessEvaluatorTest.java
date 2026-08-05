package com.wust.dormitory.subscription;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureAccessEvaluatorTest {
    @Test
    void nonControllableFeatureFollowsSystemGrant() {
        var state = FeatureAccessEvaluator.evaluate(new FeatureAccessEvaluator.Input(
                true, true, false, false, null, true));

        assertTrue(state.systemGranted());
        assertTrue(state.schoolEnabled());
        assertTrue(state.effectiveEnabled());
    }

    @Test
    void schoolCanCloseOnlyControllableGrantedFeature() {
        var state = FeatureAccessEvaluator.evaluate(new FeatureAccessEvaluator.Input(
                true, true, true, true, Boolean.FALSE, true));

        assertTrue(state.systemGranted());
        assertFalse(state.schoolEnabled());
        assertFalse(state.effectiveEnabled());
    }

    @Test
    void schoolSettingCannotCreateMissingSystemGrant() {
        var state = FeatureAccessEvaluator.evaluate(new FeatureAccessEvaluator.Input(
                true, false, true, false, Boolean.TRUE, true));

        assertFalse(state.systemGranted());
        assertTrue(state.schoolEnabled());
        assertFalse(state.effectiveEnabled());
    }

    @Test
    void unimplementedFeatureCanNeverBecomeEffective() {
        var state = FeatureAccessEvaluator.evaluate(new FeatureAccessEvaluator.Input(
                false, true, true, true, Boolean.TRUE, true));

        assertFalse(state.effectiveEnabled());
    }

    @Test
    void missingSchoolSettingUsesCatalogDefault() {
        var state = FeatureAccessEvaluator.evaluate(new FeatureAccessEvaluator.Input(
                true, true, true, false, null, true));

        assertFalse(state.schoolEnabled());
        assertFalse(state.effectiveEnabled());
    }

    @Test
    void businessStateRemainsFinalGate() {
        var state = FeatureAccessEvaluator.evaluate(new FeatureAccessEvaluator.Input(
                true, true, true, true, Boolean.TRUE, false));

        assertFalse(state.effectiveEnabled());
    }
}
