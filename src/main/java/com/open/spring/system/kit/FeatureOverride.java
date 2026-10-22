package com.open.spring.system.kit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An admin's decision to turn a feature on or off, taking precedence over KIT and FEATURES. */
@Entity
@Table(name = "feature_override")
public class FeatureOverride {

    @Id
    @Column(name = "feature_id", length = 64, nullable = false)
    private String featureId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected FeatureOverride() {
    }

    public FeatureOverride(String featureId, boolean enabled, String updatedBy, Instant updatedAt) {
        this.featureId = featureId;
        this.enabled = enabled;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public String getFeatureId() {
        return featureId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
