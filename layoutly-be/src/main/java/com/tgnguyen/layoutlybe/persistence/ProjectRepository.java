package com.tgnguyen.layoutlybe.persistence;
import org.springframework.data.jpa.repository.*;
import java.util.*;
import java.time.Instant;
public interface ProjectRepository extends JpaRepository<ProjectEntity,UUID> {
@Modifying(clearAutomatically=true, flushAutomatically=true)
@Query("update ProjectEntity p set p.openedAt = :openedAt where p.id = :id and p.ownerId = :owner and p.deletedAt is null")
int touchOpenedAt(@org.springframework.data.repository.query.Param("id") UUID id,
 @org.springframework.data.repository.query.Param("owner") UUID owner,
 @org.springframework.data.repository.query.Param("openedAt") Instant openedAt);
Optional<ProjectEntity> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID ownerId);
List<ProjectEntity> findByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(UUID ownerId);
List<ProjectEntity> findByOwnerIdAndSnapshotIdAndDeletedAtIsNull(UUID ownerId, String snapshotId);
}
