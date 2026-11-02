package com.open.spring.system.kit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

/** Applying and forgetting a whole kit at runtime, against an in-memory store. */
class KitSwitchTest {

    private static final List<String> VANILLA = List.of("core");
    private static final List<String> EDUCATION = List.of("core", "lessons", "code-runner", "assignments", "game-engine",
            "ai-chatbot", "ai-grading", "social", "calendar", "analytics", "hallpass", "resume");
    private static final List<String> BUSINESS = List.of("core", "ai-chatbot", "social", "calendar", "analytics", "resume");

    private final KitCatalog catalog = KitCatalog.load();
    private final FeatureOverrideStore store = new FeatureOverrideStore.InMemory();
    private final List<Object> events = new ArrayList<>();

    /** A fresh instance over the same store is also what a restart looks like. */
    private KitFeatures kit(String kit, String features) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("store", store);
        KitFeatures result = new KitFeatures(catalog, kit, features, key -> null,
                beans.getBeanProvider(FeatureOverrideStore.class), events::add);
        result.reloadOverrides();
        return result;
    }

    private int status(KitFeatures f, String path) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new FeatureGateFilter(f).doFilter(new MockHttpServletRequest("GET", path), response, new MockFilterChain());
        return response.getStatus();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> detail(KitFeatures f, String id) {
        return ((List<Map<String, Object>>) f.describeAdmin().get("feature_details")).stream()
                .filter(d -> d.get("id").equals(id)).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------ presets

    @Test
    void eachKitAppliesExactlyItsCatalogFeatures() {
        KitFeatures f = kit("", "");
        Map<String, List<String>> expected = Map.of("vanilla", VANILLA, "education", EDUCATION, "business", BUSINESS);
        assertEquals(expected.keySet(), catalog.kits().keySet());
        for (Map.Entry<String, List<String>> kit : expected.entrySet()) {
            f.applyKit(kit.getKey(), "admin");
            assertEquals(kit.getValue(), f.getEnabled());
            assertEquals(catalog.expand(catalog.kits().get(kit.getKey())), f.getEnabled());
            assertEquals(kit.getKey(), f.getKit());
            assertEquals(catalog.kits().get(kit.getKey()).name(), f.kitName());
            assertEquals("admin", f.kitSource());
            assertFalse(f.isCustomized());
        }
    }

    @Test
    void everyFeaturePathFollowsTheAppliedKit() throws Exception {
        KitFeatures f = kit("", "");
        for (KitCatalog.Kit kit : catalog.kits().values()) {
            f.applyKit(kit.id(), "admin");
            for (KitCatalog.Feature feature : catalog.features().values()) {
                boolean member = catalog.expand(kit).contains(feature.id());
                assertEquals(member, f.isEnabled(feature.id()), kit.id() + "/" + feature.id());
                for (String path : feature.paths()) {
                    assertEquals(member ? 200 : 404, status(f, path), kit.id() + " " + path);
                }
            }
        }
    }

    @Test
    void unknownKitsAreRejectedAndNothingChanges() {
        KitFeatures f = kit("education", "");
        f.applyOverrides(Map.of("hallpass", false), "admin");
        for (String bad : new String[] {"nope", "default", "EDUCATION", "", null}) {
            assertThrows(IllegalArgumentException.class, () -> f.applyKit(bad, "admin"), String.valueOf(bad));
        }
        assertEquals("env", f.kitSource());
        assertTrue(store.loadActiveKit().isEmpty());
        assertEquals(1, store.loadAll().size());
    }

    @Test
    void theAppliedKitIsAuditedWithWhoAndWhen() {
        kit("", "").applyKit("business", "alice");
        ActiveKit stored = store.loadActiveKit().orElseThrow();
        assertEquals("business", stored.getKitId());
        assertEquals("alice", stored.getUpdatedBy());
        assertNotNull(stored.getUpdatedAt());
    }

    // ------------------------------------------------------------------ overrides

    @Test
    void aSwitchClearsOverridesEvenToTheSameKit() {
        KitFeatures f = kit("vanilla", "");
        f.applyOverrides(Map.of("finance-sim", true), "admin");
        f.applyKit("education", "admin");
        assertFalse(f.isEnabled("finance-sim"));
        assertTrue(store.loadAll().isEmpty());

        f.applyOverrides(Map.of("hallpass", false), "admin");
        f.applyKit("education", "admin");
        assertTrue(f.isEnabled("hallpass"));
        assertFalse(f.isCustomized());
        assertTrue(store.loadAll().isEmpty());
    }

    @Test
    void customizingAfterASwitchLayersOnThePreset() {
        KitFeatures f = kit("vanilla", "");
        f.applyKit("business", "admin");
        f.applyOverrides(Map.of("finance-sim", true, "calendar", false), "admin");

        assertTrue(f.isEnabled("finance-sim"));
        assertFalse(f.isEnabled("calendar"));
        assertEquals("business", f.getKit());
        assertTrue(f.isCustomized());
        assertEquals("admin", detail(f, "finance-sim").get("source"));
        assertEquals("admin", detail(f, "calendar").get("source"));
        assertEquals("kit", detail(f, "social").get("source"));
        assertEquals("kit", detail(f, "hallpass").get("source")); // not a member: still the preset's decision

        // Survives a restart.
        KitFeatures restarted = kit("vanilla", "");
        assertEquals("business", restarted.getKit());
        assertTrue(restarted.isEnabled("finance-sim"));
        assertFalse(restarted.isEnabled("calendar"));

        restarted.clearOverride("finance-sim", "admin");
        restarted.clearOverride("calendar", "admin");
        assertEquals(BUSINESS, restarted.getEnabled());
        assertFalse(restarted.isCustomized());
    }

    @Test
    void anOverrideMatchingThePresetIsNotACustomization() {
        KitFeatures f = kit("", "");
        f.applyKit("business", "admin");
        f.applyOverrides(Map.of("calendar", true, "hallpass", false), "admin");
        assertFalse(f.isCustomized());
        assertEquals("admin", detail(f, "calendar").get("source"));
    }

    // ------------------------------------------------------------------ DELETE and precedence

    @Test
    void forgettingReturnsToTheEnvironment() {
        KitFeatures f = kit("education", "-hallpass,+finance-sim");
        f.applyKit("vanilla", "admin");
        f.applyOverrides(Map.of("calendar", true), "admin");

        f.forgetKit("admin");

        assertEquals("education", f.getKit());
        assertEquals("env", f.kitSource());
        assertTrue(f.isCustomized());
        assertFalse(f.isEnabled("hallpass"));
        assertTrue(f.isEnabled("finance-sim"));
        assertEquals("env", f.source("hallpass"));
        assertTrue(store.loadActiveKit().isEmpty());
        assertTrue(store.loadAll().isEmpty());
    }

    @Test
    void forgettingWithKitUnsetIsTheDefaultOfEveryFeature() {
        KitFeatures f = kit("", "");
        assertEquals("default", f.kitSource());
        f.applyKit("vanilla", "admin");
        f.forgetKit("admin");

        assertEquals("default", f.getKit());
        assertEquals("All features", f.kitName());
        assertEquals("default", f.kitSource());
        assertFalse(f.isCustomized());
        assertEquals(List.copyOf(catalog.features().keySet()), f.getEnabled());
    }

    @Test
    void aStoredKitIgnoresKitAndFeaturesAcrossRestarts() {
        kit("education", "").applyKit("vanilla", "admin");

        KitFeatures restarted = kit("business", "+finance-sim,-calendar");
        assertEquals("vanilla", restarted.getKit());
        assertEquals("admin", restarted.kitSource());
        assertEquals(VANILLA, restarted.getEnabled());
        assertFalse(restarted.isCustomized());
        assertEquals("kit", restarted.source("finance-sim"));

        restarted.applyOverrides(Map.of("calendar", true), "admin");
        assertEquals(List.of("core", "calendar"), restarted.getEnabled());
    }

    @Test
    void withoutAStoredKitEnvThenOverridesApply() {
        KitFeatures f = kit("business", "+finance-sim,-calendar");
        assertEquals("env", f.kitSource());
        assertTrue(f.isEnabled("finance-sim"));
        f.applyOverrides(Map.of("calendar", true, "finance-sim", false), "admin");
        assertTrue(f.isEnabled("calendar"));
        assertFalse(f.isEnabled("finance-sim"));
    }

    @Test
    void coreIsAlwaysOnEvenWithAStrayOverrideRow() {
        store.save(new FeatureOverride("core", false, "someone", null));
        KitFeatures f = kit("vanilla", "");
        assertTrue(f.isEnabled("core"));
        f.applyKit("business", "admin");
        assertTrue(f.isEnabled("core"));
    }

    @Test
    void aStoredKitNoLongerInTheCatalogFallsBackToTheEnvironment() {
        store.applyKit(new ActiveKit("retired", "admin", null));
        KitFeatures f = kit("business", "");
        assertEquals("business", f.getKit());
        assertEquals("env", f.kitSource());
    }

    // ------------------------------------------------------------------ roles and payloads

    @Test
    @SuppressWarnings("unchecked")
    void roleLabelsAndSignupRoleFollowTheKitImmediately() {
        KitFeatures f = kit("vanilla", "");
        for (KitCatalog.Kit kit : catalog.kits().values()) {
            f.applyKit(kit.id(), "admin");
            Map<String, Object> body = f.describePublic();
            assertEquals(kit.id(), body.get("kit"));
            assertEquals(kit.name(), body.get("kit_name"));
            assertEquals("admin", body.get("kit_source"));
            assertEquals(false, body.get("customized"));
            assertEquals(kit.signupRole(), body.get("signup_role"));
            assertEquals(catalog.authoritiesFor(kit.signupRole()), f.signupAuthorities());
            Map<String, Object> roles = (Map<String, Object>) body.get("roles");
            for (KitCatalog.Role role : kit.roles().values()) {
                assertEquals(Map.of("label", role.label()), roles.get(role.id()));
                assertEquals(role.label(), f.roleLabel(role.id()));
            }
        }
        f.applyKit("business", "admin");
        assertEquals("Manager", f.authorityLabel("ROLE_TEACHER"));
        f.applyKit("education", "admin");
        assertEquals("Teacher", f.authorityLabel("ROLE_TEACHER"));
        assertEquals(List.of("ROLE_USER", "ROLE_STUDENT"), f.signupAuthorities());
    }

    @Test
    void seedAccountsAndTheirProtectionDoNotMoveWithASwitch() {
        KitFeatures f = kit("vanilla", "");
        List<KitSeedAccounts.Account> seeded = KitSeedAccounts.active();
        f.applyKit("business", "admin");
        assertEquals(seeded, KitSeedAccounts.active());
        kit("vanilla", ""); // a restart with the kit still stored
        assertEquals(seeded, KitSeedAccounts.active());
    }

    @Test
    @SuppressWarnings("unchecked")
    void adminPayloadListsEveryPresetFromTheCatalog() {
        KitFeatures f = kit("vanilla", "");
        assertFalse(f.describePublic().containsKey("presets"));
        Map<String, Map<String, Object>> presets = (Map<String, Map<String, Object>>) f.describeAdmin().get("presets");
        assertEquals(catalog.kits().keySet(), presets.keySet());
        assertEquals(EDUCATION, presets.get("education").get("features"));
        assertEquals("OCS Business", presets.get("business").get("name"));
        assertEquals(catalog.kits().get("vanilla").description(), presets.get("vanilla").get("description"));
        assertEquals(Map.of("admin", Map.of("label", "Admin"), "staff", Map.of("label", "Manager"), "member", Map.of("label", "Member")),
                presets.get("business").get("roles"));
    }

    // ------------------------------------------------------------------ startup work on enable

    @Test
    void onlyNewlyEnabledFeaturesAreAnnounced() {
        KitFeatures f = kit("business", "");
        events.clear();
        f.applyKit("education", "admin");
        assertEquals(List.of("lessons", "code-runner", "assignments", "game-engine", "ai-grading", "hallpass"),
                events.stream().map(e -> ((FeatureEnabledEvent) e).featureId()).toList());

        events.clear();
        f.applyKit("education", "admin");
        assertTrue(events.isEmpty());

        f.applyKit("vanilla", "admin");
        assertTrue(events.isEmpty());

        f.forgetKit("admin");
        assertEquals(BUSINESS.size() - 1, events.size());
    }

    @Test
    void deferredStartupTasksRunOnceWhenAKitEnablesThem() throws Exception {
        KitFeatures f = kit("vanilla", "");
        List<String> ran = new ArrayList<>();
        f.runWhenEnabled("hallpass", "seeder", () -> ran.add("seeded"));
        assertTrue(ran.isEmpty());

        f.applyKit("education", "admin");
        assertEquals(List.of("seeded"), ran);
        f.applyKit("vanilla", "admin");
        f.applyKit("education", "admin");
        assertEquals(List.of("seeded"), ran);
    }

    @Test
    void aFailingSeederDoesNotUndoTheSwitch() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("store", store);
        KitFeatures f = new KitFeatures(catalog, "vanilla", "", key -> null,
                beans.getBeanProvider(FeatureOverrideStore.class), event -> {
                    throw new IllegalStateException("seed failed");
                });
        f.applyKit("education", "admin");
        assertEquals(EDUCATION, f.getEnabled());
    }
}
