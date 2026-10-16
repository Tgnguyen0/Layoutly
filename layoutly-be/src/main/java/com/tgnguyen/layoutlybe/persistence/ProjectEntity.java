package com.tgnguyen.layoutlybe.persistence;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.*;
@Entity @Table(name="projects")
public class ProjectEntity {
 @Id public UUID id;
 public UUID ownerId;
 public String name;
 public String description = "";
 public String framework = "REACT";
 public String snapshotId;
 @JdbcTypeCode(SqlTypes.JSON) public Map<String,Object> metadata = new LinkedHashMap<>();
 public Instant createdAt;
 public Instant updatedAt;
 public Instant openedAt;
 public Instant deletedAt;
 @Version public long lockVersion;
}
