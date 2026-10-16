package com.tgnguyen.layoutlybe.persistence;
import org.springframework.data.jpa.repository.*;
import java.util.*;
import java.time.Instant;
public interface ProjectVersionRepository extends JpaRepository<ProjectVersionEntity,UUID> {
List<ProjectVersionEntity> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
