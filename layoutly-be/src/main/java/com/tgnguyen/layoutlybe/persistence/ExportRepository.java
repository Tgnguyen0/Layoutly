package com.tgnguyen.layoutlybe.persistence;
import org.springframework.data.jpa.repository.*;
import java.util.*;
import java.time.Instant;
public interface ExportRepository extends JpaRepository<ExportEntity,UUID> {
List<ExportEntity> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
