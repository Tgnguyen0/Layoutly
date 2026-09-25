package com.tgnguyen.layoutlybe.dto;

import com.tgnguyen.layoutlybe.model.snapshot.FigmaSnapshot;
import com.tgnguyen.layoutlybe.model.snapshot.ImportSource;
import com.tgnguyen.layoutlybe.model.snapshot.SnapshotAssetMetadata;

import java.time.Instant;
import java.util.List;

public record SnapshotResponse(
        String snapshotId,
        String fileKey,
        String fileName,
        String figmaVersion,
        String lastModified,
        Instant fetchedAt,
        String status,
        SnapshotAssetMetadata assets,
        DesignStructureSummary structure,
        ImportSource source,
        String pageName,
        String rootNodeId,
        String rootNodeName,
        String rootNodeType,
        boolean hasReferenceImage,
        List<String> warnings
) {
    public static SnapshotResponse from(FigmaSnapshot snapshot, DesignStructureSummary structure) {
        return new SnapshotResponse(
                snapshot.getSnapshotId(),
                snapshot.getFileKey(),
                snapshot.getFileName(),
                snapshot.getFigmaVersion(),
                snapshot.getLastModified(),
                snapshot.getFetchedAt(),
                "SYNCED",
                snapshot.getAssetMetadata(),
                structure,
                snapshot.getSource(),
                snapshot.getPageName(),
                snapshot.getRootNodeId(),
                snapshot.getRootNodeName(),
                snapshot.getRootNodeType(),
                snapshot.getReferenceImagePath() != null,
                snapshot.getWarnings());
    }
}
