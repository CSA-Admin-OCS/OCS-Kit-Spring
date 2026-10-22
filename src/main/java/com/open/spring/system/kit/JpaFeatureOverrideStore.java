package com.open.spring.system.kit;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Stores overrides in the feature_override table. The table is created here when missing, so
 * databases run with ddl-auto=none (existing installs) get it too; ddl-auto=update creates
 * the same columns from {@link FeatureOverride}.
 */
@Component
public class JpaFeatureOverrideStore implements FeatureOverrideStore {

    private final FeatureOverrideRepository repository;
    private final JdbcTemplate jdbc;
    private volatile boolean tableChecked;

    public JpaFeatureOverrideStore(FeatureOverrideRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    private void ensureTable() {
        if (!tableChecked) {
            jdbc.execute("CREATE TABLE IF NOT EXISTS feature_override ("
                    + "feature_id VARCHAR(64) NOT NULL PRIMARY KEY, "
                    + "enabled BOOLEAN NOT NULL, "
                    + "updated_by VARCHAR(255), "
                    + "updated_at TIMESTAMP NULL)");
            tableChecked = true;
        }
    }

    @Override
    public List<FeatureOverride> loadAll() {
        ensureTable();
        return repository.findAll();
    }

    @Override
    public void save(FeatureOverride override) {
        ensureTable();
        repository.save(override);
    }

    @Override
    public void delete(String featureId) {
        ensureTable();
        repository.deleteById(featureId);
    }
}
