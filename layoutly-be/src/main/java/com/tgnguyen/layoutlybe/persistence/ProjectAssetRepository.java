package com.tgnguyen.layoutlybe.persistence;
import org.springframework.data.jpa.repository.*;
import java.util.*;
import java.time.Instant;
public interface ProjectAssetRepository extends JpaRepository<ProjectAssetEntity,UUID> {
List<ProjectAssetEntity> findByProjectId(UUID projectId);
Optional<ProjectAssetEntity> findByProjectIdAndSnapshotIdAndPath(UUID projectId, String snapshotId, String path);
}
