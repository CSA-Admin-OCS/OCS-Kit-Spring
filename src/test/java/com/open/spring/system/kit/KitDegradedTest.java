package com.open.spring.system.kit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/** When the stored kit and overrides cannot be read: fail closed, say so, refuse changes, recover. */
class KitDegradedTest {

    private static final String INTERNAL = "jdbc:mysql://db.internal/secret";

    /** Reads, writes or both fail while switched on. */
    static class FlakyStore extends FeatureOverrideStore.InMemory {
        boolean readsFail;
        boolean writesFail;

        private void read() {
            if (readsFail) {
                throw new IllegalStateException("connection refused " + INTERNAL);
            }
        }

        private void write() {
            if (writesFail) {
                throw new IllegalStateException("database is read only " + INTERNAL);
            }
        }

        @Override
        public List<FeatureOverride> loadAll() {
            read();
            return super.loadAll();
        }

        @Override
        public Optional<ActiveKit> loadActiveKit() {
            read();
            return super.loadActiveKit();
        }

        @Override
        public void saveAll(List<FeatureOverride> overrides) {
            write();
            super.saveAll(overrides);
        }

        @Override
        public void delete(String featureId) {
            write();
            super.delete(featureId);
        }

        @Override
        public void applyKit(ActiveKit kit) {
            write();
            super.applyKit(kit);
        }

        @Override
        public void forgetKit() {
            write();
            super.forgetKit();
        }
    }

    private final FlakyStore store = new FlakyStore();
    private final List<Object> events = new ArrayList<>();

    private KitFeatures kit(String kit, String features) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("store", store);
        KitFeatures result = new KitFeatures(KitCatalog.load(), kit, features, key -> null,
                beans.getBeanProvider(FeatureOverrideStore.class), events::add);
        result.reloadOverrides();
        return result;
    }

    @Test
    void aReadFailureEnablesOnlyAlwaysOnFeaturesAndIsReported() throws Exception {
        kit("education", "").applyKit("business", "admin");
        store.readsFail = true;

        KitFeatures f = kit("education", "+finance-sim");

        assertTrue(f.isDegraded());
        assertEquals(List.of("core"), f.getEnabled());
        assertFalse(f.isEnabled("lessons"));
        assertFalse(f.isEnabled("finance-sim"));
        assertFalse(f.isCustomized());

        Map<String, Object> admin = f.describeAdmin();
        assertEquals(true, admin.get("degraded"));
        assertEquals(KitFeatures.DEGRADED_REASON, admin.get("degraded_reason"));
        Map<String, Object> pub = f.describePublic();
        assertEquals(true, pub.get("degraded"));
        assertFalse(pub.containsKey("degraded_reason"));
        assertEquals(List.of("core"), pub.get("features"));

        ObjectMapper json = new ObjectMapper();
        assertFalse(json.writeValueAsString(admin).contains(INTERNAL));
        assertFalse(json.writeValueAsString(pub).contains(INTERNAL));
    }

    @Test
    void aFeatureAnAdminDisabledDoesNotComeBackWhenAReloadFails() {
        KitFeatures f = kit("education", "");
        f.applyOverrides(Map.of("hallpass", false), "admin");
        store.readsFail = true;
        f.reloadOverrides();
        assertFalse(f.isEnabled("hallpass"));
        assertEquals(List.of("core"), f.getEnabled());
    }

    @Test
    void aSuccessfulRetryRestoresTheStoredState() throws Exception {
        KitFeatures first = kit("vanilla", "");
        first.applyKit("business", "admin");
        first.applyOverrides(Map.of("finance-sim", true, "calendar", false), "admin");
        store.readsFail = true;

        KitFeatures f = kit("vanilla", "");
        List<String> ran = new ArrayList<>();
        f.runWhenEnabled("social", "seeder", () -> ran.add("seeded")); // deferred: only core is on
        events.clear();

        f.retryIfDegraded();
        assertTrue(f.isDegraded());
        assertTrue(ran.isEmpty());

        store.readsFail = false;
        f.retryIfDegraded();

        assertFalse(f.isDegraded());
        assertEquals("business", f.getKit());
        assertEquals("admin", f.kitSource());
        assertTrue(f.isEnabled("finance-sim"));
        assertFalse(f.isEnabled("calendar"));
        assertTrue(f.isCustomized());
        assertFalse(f.describeAdmin().containsKey("degraded_reason"));
        assertEquals(false, f.describePublic().get("degraded"));
        assertEquals(List.of("seeded"), ran);
        assertTrue(events.contains(new FeatureEnabledEvent("social")));
    }

    @Test
    void theErrorIsLoggedOnceWithoutRepeatingOnEveryRetry() {
        Logger logger = (Logger) LoggerFactory.getLogger(KitFeatures.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            store.readsFail = true;
            KitFeatures f = kit("education", "");
            f.retryIfDegraded();
            f.retryIfDegraded();
            assertEquals(1, appender.list.stream().filter(e -> e.getLevel() == Level.ERROR).count());
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void anEmptyStoreUsesTheEnvironmentAndIsNotDegraded() {
        KitFeatures f = kit("business", "+finance-sim");
        assertFalse(f.isDegraded());
        assertEquals("env", f.kitSource());
        assertTrue(f.isEnabled("finance-sim"));
        assertEquals(false, f.describeAdmin().get("degraded"));
        assertFalse(f.describeAdmin().containsKey("degraded_reason"));
    }

    @Test
    void changesAreRefusedWhileDegradedAndNothingIsWritten() {
        KitFeatures healthy = kit("education", "");
        healthy.applyKit("business", "admin");
        store.readsFail = true;
        KitFeatures f = kit("education", "");
        store.writesFail = true; // would surface differently if a write were attempted

        assertThrows(KitStateUnavailableException.class, () -> f.applyKit("vanilla", "admin"));
        assertThrows(KitStateUnavailableException.class, () -> f.forgetKit("admin"));
        assertThrows(KitStateUnavailableException.class, () -> f.applyOverrides(Map.of("calendar", true), "admin"));
        assertThrows(KitStateUnavailableException.class, () -> f.clearOverride("calendar", "admin"));
        assertTrue(f.isDegraded());

        store.readsFail = false;
        store.writesFail = false;
        assertEquals("business", kit("education", "").getKit());
    }

    @Test
    void aWriteRetriesTheReadSoItWorksOnceTheDatabaseIsBack() {
        store.readsFail = true;
        KitFeatures f = kit("education", "");
        store.readsFail = false;

        f.applyKit("vanilla", "admin");

        assertFalse(f.isDegraded());
        assertEquals("vanilla", f.getKit());
    }

    @Test
    void aFailedWriteThrowsAndChangesNothing() {
        KitFeatures f = kit("education", "");
        f.applyOverrides(Map.of("hallpass", false), "admin");
        store.writesFail = true;

        assertThrows(IllegalStateException.class, () -> f.applyKit("vanilla", "admin"));
        assertThrows(IllegalStateException.class, () -> f.forgetKit("admin"));
        assertThrows(IllegalStateException.class, () -> f.applyOverrides(Map.of("finance-sim", true), "admin"));
        assertThrows(IllegalStateException.class, () -> f.clearOverride("hallpass", "admin"));

        assertEquals("education", f.getKit());
        assertFalse(f.isEnabled("hallpass"));
        assertFalse(f.isEnabled("finance-sim"));
    }

    @Test
    void theApiAnswers503ForChangesAndKeepsReportingWhileDegraded() throws Exception {
        store.readsFail = true;
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new KitApiController(kit("education", ""))).build();

        mvc.perform(get("/api/kit")).andExpect(status().isOk())
                .andExpect(jsonPath("$.degraded").value(true))
                .andExpect(jsonPath("$.degraded_reason").doesNotExist())
                .andExpect(jsonPath("$.features.length()").value(1));
        mvc.perform(get("/api/kit/admin")).andExpect(status().isOk())
                .andExpect(jsonPath("$.degraded").value(true))
                .andExpect(jsonPath("$.degraded_reason").value(KitFeatures.DEGRADED_REASON));
        mvc.perform(put("/api/kit/admin/kit").principal(() -> "admin").contentType(MediaType.APPLICATION_JSON)
                .content("{\"kit\":\"vanilla\"}")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value(KitFeatures.DEGRADED_REASON));
        mvc.perform(delete("/api/kit/admin/kit").principal(() -> "admin")).andExpect(status().isServiceUnavailable());
        mvc.perform(put("/api/kit/admin/features").principal(() -> "admin").contentType(MediaType.APPLICATION_JSON)
                .content("{\"features\":{\"calendar\":true}}")).andExpect(status().isServiceUnavailable());
        mvc.perform(delete("/api/kit/admin/features/calendar").principal(() -> "admin")).andExpect(status().isServiceUnavailable());
    }
}
