package com.tgnguyen.layoutlybe.persistence;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="users")
public class UserEntity {
 @Id public UUID id;
 public String displayName;
 public String email;
 public String passwordHash;
 public String company = "";
 public String bio = "";
 public Instant createdAt;
 public Instant updatedAt;
 public Instant deletedAt;
 @Version public long lockVersion;
}
