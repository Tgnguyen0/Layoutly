package com.tgnguyen.layoutlybe.model.ai;

import java.util.List;

public record AiChatResponse(
        String message,
        String revisionId,
        String status, // DRAFT, ACCEPTED, REJECTED, ERROR
        List<String> modifiedFiles,
        List<CodeDiff> diffs,
        boolean buildSuccess,
        String buildDiagnostics,
        AiUsage usage
) {}
