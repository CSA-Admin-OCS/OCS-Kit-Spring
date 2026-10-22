package com.open.spring.system.kit;

import java.util.List;

/** Where admin feature overrides live; JPA in the app, in-memory in tests. */
public interface FeatureOverrideStore {

    List<FeatureOverride> loadAll();

    void save(FeatureOverride override);

    void delete(String featureId);

    /** Keeps overrides in memory only (tests, or before the database is available). */
    class InMemory implements FeatureOverrideStore {
        private final java.util.Map<String, FeatureOverride> rows = new java.util.concurrent.ConcurrentHashMap<>();

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
    }
}
