package com.open.spring.system.kit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

class KitFeaturesTest {

    private static final String SECRET = "super-secret-gemini-key-123";

    private final KitCatalog catalog = KitCatalog.load();
    private final FeatureOverrideStore store = new FeatureOverrideStore.InMemory();
    private final List<Object> events = new ArrayList<>();

    private KitFeatures kit(String kit, String features) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("store", store);
        KitFeatures result = new KitFeatures(catalog, kit, features,
                key -> "GEMINI_API_KEY".equals(key) ? SECRET : null,
                beans.getBeanProvider(FeatureOverrideStore.class), events::add);
        result.reloadOverrides();
        return result;
    }

    @Test
    void adminOverridesBeatFeaturesWhichBeatTheKitPreset() {
        KitFeatures f = kit("education", "-hallpass,+finance-sim");
        assertEquals("kit", f.source("lessons"));
        assertEquals("env", f.source("hallpass"));
        assertFalse(f.isEnabled("hallpass"));

        f.applyOverrides(Map.of("hallpass", true, "finance-sim", false), "admin");
        assertTrue(f.isEnabled("hallpass"));
        assertFalse(f.isEnabled("finance-sim"));
        assertEquals("admin", f.source("hallpass"));

        // Overrides survive a restart (reloaded from the store).
        KitFeatures restarted = kit("education", "-hallpass,+finance-sim");
        assertTrue(restarted.isEnabled("hallpass"));
        assertFalse(restarted.isEnabled("finance-sim"));

        restarted.clearOverride("hallpass", "admin");
        assertFalse(restarted.isEnabled("hallpass"));
        assertEquals("env", restarted.source("hallpass"));
    }

    @Test
    void invalidChangesAreRejectedWithoutSavingAnything() {
        KitFeatures f = kit("business", null);
        assertThrows(IllegalArgumentException.class, () -> f.applyOverrides(Map.of("core", false), "admin"));
        assertThrows(IllegalArgumentException.class, () -> f.applyOverrides(Map.of("nope", true), "admin"));
        assertThrows(IllegalArgumentException.class,
                () -> f.applyOverrides(Map.of("finance-sim", true, "core", false), "admin"));
        assertTrue(store.loadAll().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> f.clearOverride("nope", "admin"));
    }

    @Test
    void disablingAFeatureTakesEffectWithoutRestart() throws Exception {
        KitFeatures f = kit("business", null);
        FeatureGateFilter filter = new FeatureGateFilter(f);
        assertEquals(200, request(filter, "/api/calendar/events"));

        f.applyOverrides(Map.of("calendar", false), "admin");
        assertEquals(404, request(filter, "/api/calendar/events"));
        assertEquals(200, request(filter, "/api/person/get"));

        f.applyOverrides(Map.of("calendar", true), "admin");
        assertEquals(200, request(filter, "/api/calendar/events"));
    }

    private int request(FeatureGateFilter filter, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    void enablingAFeatureRunsDeferredTasksAndAnnouncesIt() throws Exception {
        KitFeatures f = kit("vanilla", null);
        List<String> ran = new ArrayList<>();
        f.runWhenEnabled("finance-sim", "seeder", () -> ran.add("seeded"));
        assertTrue(ran.isEmpty());

        f.applyOverrides(Map.of("finance-sim", true), "admin");
        assertEquals(List.of("seeded"), ran);
        assertTrue(events.contains(new FeatureEnabledEvent("finance-sim")));
    }

    @Test
    void adminPayloadReportsSetupWithoutSecretValues() throws Exception {
        KitFeatures f = kit("education", null);
        String json = new ObjectMapper().writeValueAsString(f.describeAdmin());
        assertFalse(json.contains(SECRET), "setting values must never be returned");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> details = (List<Map<String, Object>>) f.describeAdmin().get("feature_details");
        Map<String, Object> grading = details.stream().filter(d -> d.get("id").equals("ai-grading")).findFirst().orElseThrow();
        assertEquals(true, grading.get("ready"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> settings = (List<Map<String, Object>>) ((Map<String, Object>) grading.get("setup")).get("settings");
        assertFalse(settings.isEmpty());
        settings.forEach(s -> assertFalse(s.containsKey("value")));
        assertTrue(settings.stream().allMatch(s -> s.containsKey("configured")));

        Map<String, Object> hallpass = details.stream().filter(d -> d.get("id").equals("hallpass")).findFirst().orElseThrow();
        assertEquals(false, hallpass.get("ready")); // ADMIN_EMAIL is required and unset here
    }

    @Test
    void publicPayloadHasRoleLabelsPerKit() {
        assertEquals(Map.of("label", "Teacher"), roles(kit("education", null)).get("staff"));
        assertEquals(Map.of("label", "Manager"), roles(kit("business", null)).get("staff"));
        assertEquals(Map.of("label", "Editor"), roles(kit("vanilla", null)).get("staff"));
        assertEquals(Map.of("label", "Teacher"), roles(kit(null, null)).get("staff")); // KIT unset
        Map<String, Object> body = kit("business", null).describePublic();
        assertEquals("member", body.get("signup_role"));
        assertEquals("OCS Business", body.get("kit_name"));
        assertFalse(body.toString().contains("setup"));
        assertEquals("Manager", kit("business", null).authorityLabel("ROLE_TEACHER"));
        assertEquals("Member", kit("business", null).authorityLabel("ROLE_STUDENT"));
    }

    @Test
    void seedAccountsAndSignupRoleFollowTheKit() {
        kit("business", null);
        assertEquals(List.of("admin", "manager", "member"),
                KitSeedAccounts.active().stream().map(KitSeedAccounts.Account::uid).toList());
        assertEquals("Manager User", KitSeedAccounts.get("staff").defaultName());
        assertEquals(List.of("ROLE_USER", "ROLE_TEACHER"), KitSeedAccounts.get("staff").authorities());

        kit("vanilla", null);
        assertEquals(List.of("admin", "editor", "user"),
                KitSeedAccounts.active().stream().map(KitSeedAccounts.Account::uid).toList());

        KitFeatures education = kit("education", null);
        assertEquals("student", KitSeedAccounts.get("member").uid());
        assertEquals(List.of("ROLE_USER", "ROLE_STUDENT"), education.signupAuthorities());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> roles(KitFeatures f) {
        return (Map<String, Object>) f.describePublic().get("roles");
    }
}
