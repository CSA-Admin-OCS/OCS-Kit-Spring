package com.open.spring.system.kit;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Stores admin choices in the feature_override and active_kit tables. The tables are created
 * here when missing, so databases run with ddl-auto=none (existing installs) get them too;
 * ddl-auto=update creates the same columns from {@link FeatureOverride} and {@link ActiveKit}.
 */
@Component
public class JpaFeatureOverrideStore implements FeatureOverrideStore {

    private final FeatureOverrideRepository repository;
    private final ActiveKitRepository activeKits;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private volatile boolean tablesChecked;

    public JpaFeatureOverrideStore(FeatureOverrideRepository repository, ActiveKitRepository activeKits,
                                   JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.activeKits = activeKits;
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    private void ensureTables() {
        if (!tablesChecked) {
            jdbc.execute("CREATE TABLE IF NOT EXISTS feature_override ("
                    + "feature_id VARCHAR(64) NOT NULL PRIMARY KEY, "
                    + "enabled BOOLEAN NOT NULL, "
                    + "updated_by VARCHAR(255), "
                    + "updated_at TIMESTAMP NULL)");
            jdbc.execute("CREATE TABLE IF NOT EXISTS active_kit ("
                    + "id INTEGER NOT NULL PRIMARY KEY, "
                    + "kit_id VARCHAR(64) NOT NULL, "
                    + "updated_by VARCHAR(255), "
                    + "updated_at TIMESTAMP NULL)");
            tablesChecked = true;
        }
    }

    @Override
    public List<FeatureOverride> loadAll() {
        ensureTables();
        return repository.findAll();
    }

    @Override
    public void save(FeatureOverride override) {
        ensureTables();
        repository.save(override);
    }

    @Override
    public void saveAll(List<FeatureOverride> overrides) {
        ensureTables();
        repository.saveAll(overrides); // one transaction
    }

    @Override
    public void delete(String featureId) {
        ensureTables();
        repository.deleteById(featureId);
    }

    @Override
    public Optional<ActiveKit> loadActiveKit() {
        ensureTables();
        return activeKits.findById(ActiveKit.SINGLE_ROW);
    }

    @Override
    public void applyKit(ActiveKit kit) {
        ensureTables();
        transaction.executeWithoutResult(status -> {
            repository.deleteAllInBatch();
            activeKits.save(kit);
        });
    }

    @Override
    public void forgetKit() {
        ensureTables();
        transaction.executeWithoutResult(status -> {
            repository.deleteAllInBatch();
            activeKits.deleteAllInBatch();
        });
    }
}
