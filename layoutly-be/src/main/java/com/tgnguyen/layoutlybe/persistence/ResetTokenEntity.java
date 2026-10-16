package com.tgnguyen.layoutlybe.persistence;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="password_reset_tokens")
public class ResetTokenEntity {
 @Id public UUID id;
 public UUID userId;
 public String digest;
 public Instant expiresAt;
 public Instant usedAt;
}
