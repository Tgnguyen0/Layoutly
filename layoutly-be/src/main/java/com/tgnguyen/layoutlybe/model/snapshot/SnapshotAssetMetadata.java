package com.tgnguyen.layoutlybe.model.snapshot;

import java.util.List;

public record SnapshotAssetMetadata(
        List<String> imageNodeIds,
        int cachedAssetCount,
        List<String> cachedPaths
) {
}
