package com.tgnguyen.layoutlybe.model.snapshot;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record StoredSnapshotMetadata(
        String snapshotId,
        ImportSource source,
        String fileKey,
        String fileName,
        String figmaVersion,
        String lastModified,
        Instant createdAt,
        String pageName,
        String rootNodeId,
        String rootNodeName,
        String rootNodeType,
        String referenceImagePath,
        List<String> imageNodeIds,
        Map<String, String> assetPathsByNodeId,
        List<String> assetFiles,
        List<String> warnings
) {
}
