package com.tgnguyen.layoutlybe.project;

import com.tgnguyen.layoutlybe.auth.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "projects", indexes = @Index(name = "idx_projects_owner", columnList = "owner_id"))
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserAccount owner;

    @Column(nullable = false, length = 120)
    private String name;

    /** REACT hoac HTML. */
    @Column(nullable = false, length = 20)
    private String framework;

    /** Id snapshot Figma da sinh ra code nay (tuy chon, chi de tham chieu). */
    @Column(name = "snapshot_id", length = 100)
    private String snapshotId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Project() {
    }

    public Project(UserAccount owner, String name, String framework, String snapshotId) {
        this.owner = owner;
        this.name = name;
        this.framework = framework;
        this.snapshotId = snapshotId;
    }

    public void touch() { this.updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFramework() { return framework; }
    public String getSnapshotId() { return snapshotId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
