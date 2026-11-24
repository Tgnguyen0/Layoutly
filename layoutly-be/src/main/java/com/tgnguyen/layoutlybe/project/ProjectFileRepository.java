package com.tgnguyen.layoutlybe.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProjectFileRepository extends JpaRepository<ProjectFile, UUID> {
    List<ProjectFile> findByProjectIdOrderByPath(UUID projectId);

    @Modifying
    @Query("delete from ProjectFile f where f.project.id = :projectId")
    void deleteByProjectId(@Param("projectId") UUID projectId);
}
