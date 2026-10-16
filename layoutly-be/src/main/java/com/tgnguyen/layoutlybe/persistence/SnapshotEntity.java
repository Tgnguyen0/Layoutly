package com.tgnguyen.layoutlybe.persistence;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.*;
@Entity @Table(name="snapshots")
public class SnapshotEntity {
 @Id public String id;
 public UUID ownerId;
 @JdbcTypeCode(SqlTypes.JSON) public Map<String,Object> metadata = new LinkedHashMap<>();
 public Instant createdAt;
}
