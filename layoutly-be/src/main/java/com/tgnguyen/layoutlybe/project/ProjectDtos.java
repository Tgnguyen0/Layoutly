package com.tgnguyen.layoutlybe.project;

import java.time.Instant;
import java.util.List;

public final class ProjectDtos {
    private ProjectDtos() {
    }

    public record FileInput(String path, String content) {
    }

    public record CreateRequest(String name, String framework, String snapshotId, List<FileInput> files) {
    }

    /** files == null: giu nguyen file. replaceFiles == true: xoa cac file khong co trong danh sach gui len. */
    public record UpdateRequest(String name, List<FileInput> files, Boolean replaceFiles) {
    }

    public record FileView(String path, String content, Instant updatedAt) {
    }

    public record ProjectSummary(String id, String name, String framework, String snapshotId,
                                 Instant createdAt, Instant updatedAt) {
    }

    public record ProjectDetail(ProjectSummary project, List<FileView> files) {
    }

    public record Download(String filename, byte[] zip) {
    }
}
