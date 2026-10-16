package com.tgnguyen.layoutlybe.persistence;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.*;
@Entity @Table(name="project_versions")
public class ProjectVersionEntity {
 @Id public UUID id;
 public UUID projectId;
 public String snapshotId;
 public String revisionId;
 @JdbcTypeCode(SqlTypes.JSON) public Map<String,Object> designNode = new LinkedHashMap<>();
 @JdbcTypeCode(SqlTypes.JSON) public Map<String,Object> metadata = new LinkedHashMap<>();
 public Instant createdAt;
}
