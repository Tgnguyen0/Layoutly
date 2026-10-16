package com.tgnguyen.layoutlybe.persistence;
import org.springframework.data.jpa.repository.*;
import java.util.*;
import java.time.Instant;
public interface SnapshotRepository extends JpaRepository<SnapshotEntity,String> {
Optional<SnapshotEntity> findByIdAndOwnerId(String id, UUID ownerId);
}
