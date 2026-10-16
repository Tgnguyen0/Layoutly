package com.tgnguyen.layoutlybe.persistence;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.*;
@Entity @Table(name="project_assets")
public class ProjectAssetEntity {
 @Id public UUID id;
 public UUID projectId;
 public String snapshotId;
 public String path;
 public long byteSize;
 public String sha256;
 @JdbcTypeCode(SqlTypes.JSON) public Map<String,Object> metadata = new LinkedHashMap<>();
}
