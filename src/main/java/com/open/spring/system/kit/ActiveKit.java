package com.open.spring.system.kit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The kit preset an admin applied at runtime (at most one row), with who applied it and when. Wins over KIT and FEATURES. */
@Entity
@Table(name = "active_kit")
public class ActiveKit {

    static final int SINGLE_ROW = 1;

    @Id
    @Column(name = "id", nullable = false)
    private Integer id = SINGLE_ROW;

    @Column(name = "kit_id", length = 64, nullable = false)
    private String kitId;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected ActiveKit() {
    }

    public ActiveKit(String kitId, String updatedBy, Instant updatedAt) {
        this.kitId = kitId;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public String getKitId() {
        return kitId;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
