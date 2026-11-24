package com.tgnguyen.layoutlybe.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
public class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Luon luu dang chu thuong, da trim. */
    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** Phien ban dieu khoan nguoi dung da dong y luc dang ky. */
    @Column(name = "accepted_terms_version", length = 20)
    private String acceptedTermsVersion;

    @Column(name = "accepted_terms_at")
    private Instant acceptedTermsAt;

    protected UserAccount() {
    }

    public UserAccount(String email, String displayName, String passwordHash, String acceptedTermsVersion) {
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.acceptedTermsVersion = acceptedTermsVersion;
        this.acceptedTermsAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getCreatedAt() { return createdAt; }
}
