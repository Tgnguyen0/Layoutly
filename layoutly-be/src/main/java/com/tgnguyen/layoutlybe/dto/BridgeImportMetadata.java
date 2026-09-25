package com.tgnguyen.layoutlybe.dto;

import com.tgnguyen.layoutlybe.model.snapshot.ImportSource;

import java.util.List;

public record BridgeImportMetadata(
        ImportSource source,
        String fileKey,
        String fileName,
        String pageName,
        String nodeId,
        String nodeName,
        String nodeType,
        String exportedAt,
        List<BridgeAssetDescriptor> assets,
        List<String> warnings
) {
    public record BridgeAssetDescriptor(String nodeId, String fileName, String contentType) {
    }
}
