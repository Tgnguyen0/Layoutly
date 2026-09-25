package com.tgnguyen.layoutlybe.dto;

import com.tgnguyen.layoutlybe.model.snapshot.ImportSource;

import java.time.Instant;
import java.util.List;

public record BridgeImportResponse(
        String snapshotId,
        String fileName,
        String nodeName,
        ImportSource source,
        int nodeCount,
        Instant createdAt,
        List<String> warnings
) {
}
