package com.open.spring.system.kit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class KitResolverTest {

    private final KitCatalog catalog = KitCatalog.load();

    @Test
    void unsetMeansEveryFeature() {
        KitResolver.Resolution r = KitResolver.resolve(catalog, null, null);
        assertEquals("default", r.kit());
        assertEquals(List.copyOf(catalog.features().keySet()), r.enabled());
        assertFalse(r.customized());
    }

    @Test
    void vanillaIsCoreOnly() {
        KitResolver.Resolution r = KitResolver.resolve(catalog, "Vanilla", "");
        assertEquals("vanilla", r.kit());
        assertEquals(List.of("core"), r.enabled());
    }

    @Test
    void businessLeavesOutEducationFeatures() {
        KitResolver.Resolution r = KitResolver.resolve(catalog, "business", null);
        for (String id : r.enabled()) {
            assertFalse("education".equals(catalog.features().get(id).category()), id);
        }
        assertTrue(r.enabled().contains("ai-chatbot"));
        assertFalse(r.customized());
    }

    @Test
    void relativeOverridesApplyToThePreset() {
        KitResolver.Resolution r = KitResolver.resolve(catalog, "education", "+finance-sim, -hallpass");
        assertTrue(r.enabled().contains("finance-sim"));
        assertFalse(r.enabled().contains("hallpass"));
        assertTrue(r.enabled().contains("lessons"));
        assertTrue(r.customized());
    }

    @Test
    void plainIdsFormAnExplicitList() {
        KitResolver.Resolution r = KitResolver.resolve(catalog, "business", "lessons,code-runner");
        assertEquals(List.of("core", "lessons", "code-runner"), r.enabled());
    }

    @Test
    void starMeansEverythingAndCanBeTrimmed() {
        KitResolver.Resolution r = KitResolver.resolve(catalog, "vanilla", "*,-face-recognition");
        assertFalse(r.enabled().contains("face-recognition"));
        assertEquals(catalog.features().size() - 1, r.enabled().size());
    }

    @Test
    void mistakesFailFast() {
        assertThrows(IllegalStateException.class, () -> KitResolver.resolve(catalog, "enterprise", null));
        assertThrows(IllegalStateException.class, () -> KitResolver.resolve(catalog, null, "+lesons"));
        assertThrows(IllegalStateException.class, () -> KitResolver.resolve(catalog, null, "-core"));
    }

    @Test
    void pathsResolveByLongestPrefixOnSegmentBoundaries() {
        assertEquals("calendar", catalog.featureForPath("/api/calendar/events"));
        assertEquals("examples", catalog.featureForPath("/api/calendar/isLeapYear/2024"));
        assertEquals("finance-sim", catalog.featureForPath("/bank/read"));
        assertEquals("ai-grading", catalog.featureForPath("/api/submissions/ai-grade/7"));
        assertEquals("assignments", catalog.featureForPath("/api/submissions/7"));
        assertEquals(null, catalog.featureForPath("/bankrupt"));
        assertEquals("finance-sim", catalog.featureForClassName("com.open.spring.mvc.cryptoMining.MiningService"));
        assertEquals("core", catalog.featureForClassName("com.open.spring.system.ModelInit"));
        assertEquals(null, catalog.featureForClassName("org.example.Other"));
    }
}
