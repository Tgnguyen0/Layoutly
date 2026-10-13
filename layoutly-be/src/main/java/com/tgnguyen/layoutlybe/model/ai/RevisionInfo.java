package com.tgnguyen.layoutlybe.model.ai;

import java.time.Instant;
import java.util.List;

public record RevisionInfo(
        String revisionId,
        String parentRevisionId,
        String snapshotId,
        String status, // DRAFT, ACCEPTED, REJECTED
        String targetFramework, // REACT, HTML
        String prompt,
        Instant createdAt,
        List<String> modifiedFiles,
        boolean buildSuccess,
        String buildDiagnostics,
        AiUsage usage
) {}
