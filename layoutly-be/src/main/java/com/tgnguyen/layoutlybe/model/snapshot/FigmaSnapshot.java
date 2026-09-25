package com.tgnguyen.layoutlybe.model.snapshot;

import com.tgnguyen.layoutlybe.model.ir.DesignNode;
import com.tgnguyen.layoutlybe.service.AssetExportService;

import java.time.Instant;
import java.util.List;

public class FigmaSnapshot {
    private final String snapshotId;
    private final ImportSource source;
    private final String fileKey;
    private final String fileName;
    private final String figmaVersion;
    private final String lastModified;
    private final Instant fetchedAt;
    private final String pageName;
    private final String rootNodeId;
    private final String rootNodeName;
    private final String rootNodeType;
    private final String referenceImagePath;
    private final List<String> warnings;
    private final String rawJson;
    private final DesignNode designIr;
    private final List<String> imageNodeIds;
    private volatile AssetExportService.AssetBundle assets;

    public FigmaSnapshot(String snapshotId, String fileKey, String fileName, String figmaVersion,
                         String lastModified, Instant fetchedAt, String rawJson, DesignNode designIr,
                         List<String> imageNodeIds) {
        this(snapshotId, ImportSource.FIGMA_REST, fileKey, fileName, figmaVersion, lastModified,
                fetchedAt, null, designIr == null ? null : designIr.getId(),
                designIr == null ? null : designIr.getName(), designIr == null ? null : designIr.getType(),
                null, List.of(), rawJson, designIr, imageNodeIds);
    }

    public FigmaSnapshot(String snapshotId, ImportSource source, String fileKey, String fileName,
                         String figmaVersion, String lastModified, Instant fetchedAt, String pageName,
                         String rootNodeId, String rootNodeName, String rootNodeType,
                         String referenceImagePath, List<String> warnings, String rawJson,
                         DesignNode designIr, List<String> imageNodeIds) {
        this.snapshotId = snapshotId;
        this.source = source;
        this.fileKey = fileKey;
        this.fileName = fileName;
        this.figmaVersion = figmaVersion;
        this.lastModified = lastModified;
        this.fetchedAt = fetchedAt;
        this.pageName = pageName;
        this.rootNodeId = rootNodeId;
        this.rootNodeName = rootNodeName;
        this.rootNodeType = rootNodeType;
        this.referenceImagePath = referenceImagePath;
        this.warnings = warnings == null ? List.of() : List.copyOf(warnings);
        this.rawJson = rawJson;
        this.designIr = designIr;
        this.imageNodeIds = imageNodeIds == null ? List.of() : List.copyOf(imageNodeIds);
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public ImportSource getSource() {
        return source;
    }

    public String getFileKey() {
        return fileKey;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFigmaVersion() {
        return figmaVersion;
    }

    public String getLastModified() {
        return lastModified;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public String getPageName() {
        return pageName;
    }

    public String getRootNodeId() {
        return rootNodeId;
    }

    public String getRootNodeName() {
        return rootNodeName;
    }

    public String getRootNodeType() {
        return rootNodeType;
    }

    public String getReferenceImagePath() {
        return referenceImagePath;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public String getRawJson() {
        return rawJson;
    }

    public DesignNode getDesignIr() {
        return designIr;
    }

    public List<String> getImageNodeIds() {
        return imageNodeIds;
    }

    public AssetExportService.AssetBundle getAssets() {
        return assets;
    }

    public void cacheAssets(AssetExportService.AssetBundle assets) {
        this.assets = assets;
    }

    public SnapshotAssetMetadata getAssetMetadata() {
        AssetExportService.AssetBundle cached = assets;
        return new SnapshotAssetMetadata(
                imageNodeIds,
                cached == null ? 0 : cached.zipAssetByPath().size(),
                cached == null ? List.of() : List.copyOf(cached.zipAssetByPath().keySet()));
    }
}
