package com.tgnguyen.layoutlybe.persistence;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="user_sessions")
public class SessionEntity {
 @Id public UUID id;
 public UUID userId;
 public String digest;
 public Instant createdAt;
 public Instant expiresAt;
 public boolean rememberMe;
 public String device;
 public String kind = "WEB";
 public String scopeSnapshotId;
}
