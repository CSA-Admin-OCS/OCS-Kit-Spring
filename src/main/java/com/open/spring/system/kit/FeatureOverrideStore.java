package com.open.spring.system.kit;

import java.util.List;
import java.util.Optional;

/** Where admin choices live (feature overrides and the applied kit); JPA in the app, in-memory in tests. */
public interface FeatureOverrideStore {

    List<FeatureOverride> loadAll();

    void save(FeatureOverride override);

    void delete(String featureId);

    /** The kit an admin applied, if any. */
    Optional<ActiveKit> loadActiveKit();

    /** Atomically stores the applied kit and removes every feature override. */
    void applyKit(ActiveKit kit);

    /** Atomically forgets the applied kit and every feature override. */
    void forgetKit();

    /** Keeps everything in memory only (tests, or before the database is available). */
    class InMemory implements FeatureOverrideStore {
        private final java.util.Map<String, FeatureOverride> rows = new java.util.concurrent.ConcurrentHashMap<>();
        private volatile ActiveKit activeKit;

        @Override
        public List<FeatureOverride> loadAll() {
            return List.copyOf(rows.values());
        }

        @Override
        public void save(FeatureOverride override) {
            rows.put(override.getFeatureId(), override);
        }

        @Override
        public void delete(String featureId) {
            rows.remove(featureId);
        }

        @Override
        public Optional<ActiveKit> loadActiveKit() {
            return Optional.ofNullable(activeKit);
        }

        @Override
        public void applyKit(ActiveKit kit) {
            rows.clear();
            activeKit = kit;
        }

        @Override
        public void forgetKit() {
            rows.clear();
            activeKit = null;
        }
    }
}
