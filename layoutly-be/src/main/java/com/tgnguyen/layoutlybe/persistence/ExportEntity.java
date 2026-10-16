package com.tgnguyen.layoutlybe.persistence;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.*;
@Entity @Table(name="exports")
public class ExportEntity {
 @Id public UUID id;
 public UUID projectId;
 public String snapshotId;
 public String revisionId;
 public String format;
 public long byteSize;
 public Instant createdAt;
 @JdbcTypeCode(SqlTypes.JSON) public Map<String,Object> metadata = new LinkedHashMap<>();
}
