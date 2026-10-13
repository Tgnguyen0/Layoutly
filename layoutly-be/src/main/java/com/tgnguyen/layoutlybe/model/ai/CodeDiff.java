package com.tgnguyen.layoutlybe.model.ai;

import java.util.List;

public record CodeDiff(
        String filePath,
        String changeType, // ADDED, MODIFIED, DELETED
        String oldContent,
        String newContent,
        String patchUnified,
        List<Hunk> hunks
) {
    public record Hunk(
            int oldStart,
            int oldLength,
            int newStart,
            int newLength,
            List<String> lines
    ) {}
}
