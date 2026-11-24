package com.tgnguyen.layoutlybe.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/** Mot file ma nguon (text) cua du an, vi du "src/App.jsx" hoac "styles.css". */
@Entity
@Table(name = "project_files", uniqueConstraints =
        @UniqueConstraint(name = "uk_project_files_path", columnNames = {"project_id", "path"}))
public class ProjectFile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, length = 300)
    private String path;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ProjectFile() {
    }

    public ProjectFile(Project project, String path, String content) {
        this.project = project;
        this.path = path;
        setContent(content);
    }

    public void setContent(String content) {
        this.content = content;
        this.sizeBytes = content.getBytes(StandardCharsets.UTF_8).length;
        this.updatedAt = Instant.now();
    }

    public String getPath() { return path; }
    public String getContent() { return content; }
    public int getSizeBytes() { return sizeBytes; }
    public Instant getUpdatedAt() { return updatedAt; }
}
