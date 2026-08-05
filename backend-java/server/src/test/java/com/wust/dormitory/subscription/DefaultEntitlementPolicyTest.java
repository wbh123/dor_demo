package com.wust.dormitory.subscription;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultEntitlementPolicyTest {
    private static final List<DefaultEntitlementPolicy.CatalogFeature> CATALOG = List.of(
            new DefaultEntitlementPolicy.CatalogFeature("IMPLEMENTED_DEFAULT", true, true, true),
            new DefaultEntitlementPolicy.CatalogFeature("IMPLEMENTED_OFF", true, true, false),
            new DefaultEntitlementPolicy.CatalogFeature("IMPLEMENTED_FIXED", true, false, false),
            new DefaultEntitlementPolicy.CatalogFeature("PLACEHOLDER", false, true, true));

    @Test
    void defaultPlanContainsEveryImplementedFeatureAndNoPlaceholder() {
        Set<String> features = DefaultEntitlementPolicy.defaultPlanFeatures(CATALOG);

        assertEquals(Set.of("IMPLEMENTED_DEFAULT", "IMPLEMENTED_OFF", "IMPLEMENTED_FIXED"), features);
        assertFalse(features.contains("PLACEHOLDER"));
    }

    @Test
    void schoolDefaultsOnlyInitializeGrantedControllableDefaultEnabledFeatures() {
        var settings = DefaultEntitlementPolicy.initialSchoolSettings(
                CATALOG,
                Set.of("IMPLEMENTED_DEFAULT", "IMPLEMENTED_OFF", "PLACEHOLDER"));

        assertEquals(Boolean.TRUE, settings.get("IMPLEMENTED_DEFAULT"));
        assertFalse(settings.containsKey("IMPLEMENTED_OFF"));
        assertFalse(settings.containsKey("IMPLEMENTED_FIXED"));
        assertFalse(settings.containsKey("PLACEHOLDER"));
    }

    @Test
    void systemRevocationAlwaysWinsWhileSchoolPreferenceCanBeRetained() {
        var state = FeatureAccessEvaluator.evaluate(new FeatureAccessEvaluator.Input(
                true, false, true, true, Boolean.TRUE, true));
        assertTrue(state.schoolEnabled());
        assertFalse(state.effectiveEnabled());

        var restored = FeatureAccessEvaluator.evaluate(new FeatureAccessEvaluator.Input(
                true, true, true, true, Boolean.TRUE, true));
        assertTrue(restored.effectiveEnabled());
    }
}
