package com.tgnguyen.layoutlybe.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgnguyen.layoutlybe.model.ir.DesignNode;
import com.tgnguyen.layoutlybe.model.snapshot.FigmaSnapshot;
import com.tgnguyen.layoutlybe.model.snapshot.ImportSource;
import com.tgnguyen.layoutlybe.model.snapshot.StoredSnapshotMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FigmaSnapshotService {
    private final FigmaService figmaService;
    private final FigmaParserService parserService;
    private final AssetExportService assetExportService;
    private final ObjectMapper objectMapper;
    private final SnapshotStorageService storageService;

    private final Map<String, FigmaSnapshot> snapshotsById = new ConcurrentHashMap<>();
    private final Map<String, String> snapshotIdByFileKey = new ConcurrentHashMap<>();
    private final Map<String, String> tokenBySnapshotId = new ConcurrentHashMap<>();
    private final Map<String, Mono<FigmaSnapshot>> importFlights = new ConcurrentHashMap<>();
    private final Map<String, Mono<FigmaSnapshot>> refreshFlights = new ConcurrentHashMap<>();
    private final Map<String, Mono<AssetExportService.AssetBundle>> assetFlights = new ConcurrentHashMap<>();

    @Autowired
    public FigmaSnapshotService(FigmaService figmaService,
                                FigmaParserService parserService,
                                AssetExportService assetExportService,
                                ObjectMapper objectMapper,
                                SnapshotStorageService storageService) {
        this.figmaService = figmaService;
        this.parserService = parserService;
        this.assetExportService = assetExportService;
        this.objectMapper = objectMapper;
        this.storageService = storageService;
    }

    public FigmaSnapshotService(FigmaService figmaService,
                                FigmaParserService parserService,
                                AssetExportService assetExportService,
                                ObjectMapper objectMapper) {
        this(figmaService, parserService, assetExportService, objectMapper,
                new SnapshotStorageService(objectMapper, Path.of(
                        System.getProperty("java.io.tmpdir"), "layoutly-tests", UUID.randomUUID().toString())));
    }

    public Mono<FigmaSnapshot> importSnapshot(String fileKey, String token) {
        return importSnapshot(fileKey, null, token);
    }

    public Mono<FigmaSnapshot> importSnapshot(String fileKey, String nodeId, String token) {
        String normalizedKey = normalizeFileKey(fileKey);
        String normalizedNodeId = normalizeNodeId(nodeId);
        String cacheKey = importKey(normalizedKey, normalizedNodeId);
        Optional<FigmaSnapshot> cached = normalizedNodeId == null
                ? findByFileKey(normalizedKey)
                : findByImportKey(cacheKey);
        if (cached.isPresent()) {
            rememberToken(cached.get().getSnapshotId(), token);
            FigmaSnapshot snapshot = cached.get();
            return hasText(token) && !snapshot.getImageNodeIds().isEmpty()
                    ? retryMissingAssets(snapshot, token) : Mono.just(snapshot);
        }

        return importFlights.computeIfAbsent(cacheKey, key ->
                figmaService.getFile(normalizedKey, token)
                        .map(rawJson -> createSnapshot(
                                UUID.randomUUID().toString(),
                                normalizedKey,
                                normalizedNodeId,
                                rawJson))
                        .doOnNext(snapshot -> storeSnapshot(snapshot, token))
                        .doFinally(signal -> importFlights.remove(cacheKey))
                        .cache());
    }

    public Mono<FigmaSnapshot> getOrImportByFileKey(String fileKey, String token) {
        return importSnapshot(fileKey, token);
    }

    /** HTTP imports always get a private snapshot ID. Raw Figma cache is token-scoped. */
    public Mono<FigmaSnapshot> importPrivateSnapshot(String fileKey, String nodeId, String token) {
        String key = normalizeFileKey(fileKey);
        return figmaService.getFile(key, token)
                .map(raw -> createSnapshot(UUID.randomUUID().toString(), key, normalizeNodeId(nodeId), raw))
                .doOnNext(snapshot -> storeSnapshot(snapshot, token));
    }

    public FigmaSnapshot copySnapshot(String id) {
        FigmaSnapshot original = requireSnapshot(id);
        String nextId = UUID.randomUUID().toString();
        FigmaSnapshot copy = new FigmaSnapshot(nextId, original.getSource(), original.getFileKey(),
                original.getFileName(), original.getFigmaVersion(), original.getLastModified(), Instant.now(),
                original.getPageName(), original.getRootNodeId(), original.getRootNodeName(), original.getRootNodeType(),
                original.getReferenceImagePath(), original.getWarnings(), original.getRawJson(),
                objectMapper.convertValue(original.getDesignIr(), DesignNode.class), original.getImageNodeIds());
        copy.cacheAssets(original.getAssets());
        storeSnapshot(copy, tokenBySnapshotId.get(id), storageService.referenceImage(id));
        return copy;
    }

    public Mono<FigmaSnapshot> refresh(String snapshotId, String token) {
        FigmaSnapshot current = requireSnapshot(snapshotId);
        if (current.getSource() != ImportSource.FIGMA_REST) {
            return Mono.error(new IllegalArgumentException(
                    "Snapshot tu Layoutly Bridge can duoc dong bo lai trong Figma Plugin."));
        }
        String effectiveToken = hasText(token) ? token : tokenBySnapshotId.get(snapshotId);
        rememberToken(snapshotId, token);

        return refreshFlights.computeIfAbsent(snapshotId, id ->
                figmaService.fetchFileFresh(current.getFileKey(), effectiveToken)
                        .map(rawJson -> createSnapshot(id, current.getFileKey(), current.getRootNodeId(), rawJson))
                        .doOnNext(snapshot -> storeSnapshot(snapshot, effectiveToken))
                        .doFinally(signal -> refreshFlights.remove(id))
                        .cache());
    }

    public FigmaSnapshot requireSnapshot(String snapshotId) {
        FigmaSnapshot snapshot = snapshotsById.get(snapshotId);
        if (snapshot != null) return snapshot;

        FigmaSnapshot loaded = storageService.load(snapshotId)
                .map(this::restoreSnapshot)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay snapshot: " + snapshotId));
        snapshotsById.put(snapshotId, loaded);
        if (loaded.getSource() == ImportSource.FIGMA_REST && hasText(loaded.getFileKey())) {
            rememberSnapshotKey(loaded);
        }
        return loaded;
    }

    public Optional<FigmaSnapshot> findByFileKey(String fileKey) {
        String normalizedKey = normalizeFileKey(fileKey);
        String snapshotId = snapshotIdByFileKey.get(normalizedKey);
        if (snapshotId != null) return Optional.of(requireSnapshot(snapshotId));
        return storageService.findLatestRestSnapshotId(normalizedKey)
                .map(this::requireSnapshot)
                .filter(this::isFullSnapshot);
    }

    public Mono<AssetExportService.AssetBundle> getAssets(String snapshotId) {
        FigmaSnapshot snapshot = requireSnapshot(snapshotId);
        if (snapshot.getAssets() != null) return Mono.just(snapshot.getAssets());
        if (snapshot.getSource() == ImportSource.FIGMA_PLUGIN) {
            return Mono.just(new AssetExportService.AssetBundle(Map.of(), Map.of()));
        }

        Optional<AssetExportService.AssetBundle> reusable = storageService.findReusableAssets(
                snapshot.getFileKey(), snapshot.getFigmaVersion(), snapshot.getImageNodeIds());
        if (reusable.isPresent()) {
            snapshot.cacheAssets(reusable.get());
            storageService.saveAssets(snapshot, reusable.get());
            return Mono.just(reusable.get());
        }

        return assetFlights.computeIfAbsent(snapshotId, id ->
                assetExportService.exportAssets(
                                snapshot.getFileKey(),
                                tokenBySnapshotId.get(snapshotId),
                                snapshot.getDesignIr())
                        .doOnNext(assets -> {
                            snapshot.cacheAssets(assets);
                            storageService.saveAssets(snapshot, assets);
                        })
                        .doFinally(signal -> assetFlights.remove(id))
                        .cache());
    }

    public Mono<Map<String, String>> getPreviewImageUrls(String snapshotId, DesignNode previewRoot) {
        FigmaSnapshot snapshot = requireSnapshot(snapshotId);
        if (snapshot.getImageNodeIds().isEmpty()) return Mono.just(Map.of());
        // Another import may have recovered a missing image for this same immutable Figma version.
        if (snapshot.getAssets() != null && missingAssets(previewRoot, snapshot.getAssets().cssUrlByNodeId()) > 0) {
            storageService.findReusableAssets(snapshot.getFileKey(), snapshot.getFigmaVersion(), snapshot.getImageNodeIds())
                    .filter(reusable -> reusable.cssUrlByNodeId().size() > snapshot.getAssets().cssUrlByNodeId().size())
                    .ifPresent(reusable -> { snapshot.cacheAssets(reusable); storageService.saveAssets(snapshot, reusable); });
        }
        // Persist bytes before rendering, so reload/restart cannot turn the preview into missing images.
        // Propagate failures instead of presenting a successful, blank design.
        return getAssets(snapshotId).map(assets -> {
            if (snapshot.getSource() == ImportSource.FIGMA_REST && assets.cssUrlByNodeId().isEmpty())
                throw new IllegalStateException("Chưa tải được ảnh thiết kế. Hãy nhập lại cùng URL và Figma token để tải tài nguyên.");
            return toLocalPreviewUrls(snapshot);
        });
    }

    public Map<String, String> getLocalAssetUrls(String snapshotId) {
        FigmaSnapshot snapshot = requireSnapshot(snapshotId);
        return snapshot.getAssets() == null ? Map.of() : toLocalPreviewUrls(snapshot);
    }

    private Mono<FigmaSnapshot> retryMissingAssets(FigmaSnapshot snapshot, String token) {
        return getAssets(snapshot.getSnapshotId()).flatMap(existing -> {
            java.util.Set<String> missing = new java.util.LinkedHashSet<>(snapshot.getImageNodeIds());
            missing.removeAll(existing.cssUrlByNodeId().keySet());
            if (missing.isEmpty()) return Mono.just(snapshot);
            return assetExportService.exportMissingAssets(snapshot.getFileKey(), token, snapshot.getDesignIr(), missing)
                    .map(downloaded -> {
                        Map<String, String> paths = new java.util.LinkedHashMap<>(existing.cssUrlByNodeId());
                        Map<String, byte[]> files = new java.util.LinkedHashMap<>(existing.zipAssetByPath());
                        paths.putAll(downloaded.cssUrlByNodeId()); files.putAll(downloaded.zipAssetByPath());
                        AssetExportService.AssetBundle combined = new AssetExportService.AssetBundle(paths, files);
                        snapshot.cacheAssets(combined);
                        storageService.saveAssets(snapshot, combined);
                        return snapshot;
                    });
        });
    }

    public int missingPreviewAssets(String snapshotId, DesignNode root) {
        FigmaSnapshot snapshot = requireSnapshot(snapshotId);
        return missingAssets(root, snapshot.getAssets() == null ? Map.of() : snapshot.getAssets().cssUrlByNodeId());
    }

    private int missingAssets(DesignNode node, Map<String, String> urls) {
        if (node == null || (node.getStyle() != null && Boolean.FALSE.equals(node.getStyle().getVisible()))) return 0;
        if (node.getAsset() != null && node.getAsset().exportAsImage()) return urls.containsKey(node.getId()) ? 0 : 1;
        return node.getChildren() == null ? 0 : node.getChildren().stream().mapToInt(child -> missingAssets(child, urls)).sum();
    }

    public FigmaSnapshot storeImportedSnapshot(FigmaSnapshot snapshot,
                                               AssetExportService.AssetBundle assets,
                                               byte[] referenceImage) {
        snapshot.cacheAssets(assets == null
                ? new AssetExportService.AssetBundle(Map.of(), Map.of())
                : assets);
        storeSnapshot(snapshot, null, referenceImage);
        return snapshot;
    }

    private FigmaSnapshot createSnapshot(String snapshotId, String fileKey, String rawJson) {
        return createSnapshot(snapshotId, fileKey, null, rawJson);
    }

    private FigmaSnapshot createSnapshot(String snapshotId, String fileKey, String nodeId, String rawJson) {
        try {
            JsonNode response = objectMapper.readTree(rawJson);
            DesignNode designIr = parserService.parseDesignTree(rawJson);
            DesignNode rootNode = hasText(nodeId) ? findNodeById(designIr, nodeId) : designIr;
            if (rootNode == null) {
                throw new IllegalArgumentException("Khong tim thay node-id trong file Figma: " + nodeId);
            }
            List<String> imageNodeIds = new ArrayList<>();
            collectImageNodeIds(designIr, imageNodeIds);
            return new FigmaSnapshot(
                    snapshotId,
                    ImportSource.FIGMA_REST,
                    fileKey,
                    textOrFallback(response, "name", fileKey),
                    textOrFallback(response, "version", "unknown"),
                    textOrFallback(response, "lastModified", null),
                    Instant.now(),
                    null,
                    rootNode.getId(),
                    rootNode.getName(),
                    rootNode.getType(),
                    null,
                    List.of(),
                    rawJson,
                    designIr,
                    imageNodeIds);
        } catch (Exception exception) {
            throw new RuntimeException("Khong the tao Figma snapshot: " + exception.getMessage(), exception);
        }
    }

    private void storeSnapshot(FigmaSnapshot snapshot, String token) {
        storeSnapshot(snapshot, token, null);
    }

    private void storeSnapshot(FigmaSnapshot snapshot, String token, byte[] referenceImage) {
        snapshotsById.put(snapshot.getSnapshotId(), snapshot);
        if (snapshot.getSource() == ImportSource.FIGMA_REST && hasText(snapshot.getFileKey())) {
            rememberSnapshotKey(snapshot);
        }
        rememberToken(snapshot.getSnapshotId(), token);
        storageService.save(snapshot, referenceImage);
        if (snapshot.getAssets() != null) storageService.saveAssets(snapshot, snapshot.getAssets());
    }

    private FigmaSnapshot restoreSnapshot(SnapshotStorageService.StoredSnapshot stored) {
        try {
            StoredSnapshotMetadata metadata = stored.metadata();
            DesignNode designIr = parserService.parseDesignTree(stored.rawJson());
            FigmaSnapshot snapshot = new FigmaSnapshot(
                    metadata.snapshotId(), metadata.source(), metadata.fileKey(), metadata.fileName(),
                    metadata.figmaVersion(), metadata.lastModified(), metadata.createdAt(), metadata.pageName(),
                    metadata.rootNodeId(), metadata.rootNodeName(), metadata.rootNodeType(),
                    metadata.referenceImagePath(), metadata.warnings(), stored.rawJson(), designIr,
                    metadata.imageNodeIds());
            snapshot.cacheAssets(stored.assets());
            return snapshot;
        } catch (Exception exception) {
            throw new RuntimeException("Khong the khoi phuc snapshot: " + exception.getMessage(), exception);
        }
    }

    private Map<String, String> toLocalPreviewUrls(FigmaSnapshot snapshot) {
        Map<String, String> urls = new java.util.LinkedHashMap<>();
        snapshot.getAssets().cssUrlByNodeId().forEach((nodeId, assetPath) -> {
            String fileName = Path.of(assetPath).getFileName().toString();
            urls.put(nodeId, "/api/snapshots/" + snapshot.getSnapshotId() + "/assets/" + fileName);
        });
        return urls;
    }

    private void rememberToken(String snapshotId, String token) {
        if (hasText(token)) tokenBySnapshotId.put(snapshotId, token);
    }

    private Optional<FigmaSnapshot> findByImportKey(String importKey) {
        String snapshotId = snapshotIdByFileKey.get(importKey);
        if (snapshotId != null) return Optional.of(requireSnapshot(snapshotId));
        String[] parts = importKey.split("::", 2);
        return parts.length == 2 ? storageService.findLatestRestSnapshotId(parts[0], parts[1]).map(this::requireSnapshot) : Optional.empty();
    }

    private void rememberSnapshotKey(FigmaSnapshot snapshot) {
        String normalizedKey = normalizeFileKey(snapshot.getFileKey());
        snapshotIdByFileKey.put(importKey(normalizedKey, snapshot.getRootNodeId()), snapshot.getSnapshotId());
        if (isFullSnapshot(snapshot)) snapshotIdByFileKey.put(normalizedKey, snapshot.getSnapshotId());
    }

    private boolean isFullSnapshot(FigmaSnapshot snapshot) {
        return snapshot != null
                && snapshot.getDesignIr() != null
                && Objects.equals(snapshot.getRootNodeId(), snapshot.getDesignIr().getId());
    }

    private DesignNode findNodeById(DesignNode node, String nodeId) {
        if (node == null || !hasText(nodeId)) return null;
        if (nodeId.equals(node.getId())) return node;
        if (node.getChildren() == null) return null;
        for (DesignNode child : node.getChildren()) {
            DesignNode found = findNodeById(child, nodeId);
            if (found != null) return found;
        }
        return null;
    }

    private String importKey(String fileKey, String nodeId) {
        String normalizedNodeId = normalizeNodeId(nodeId);
        return normalizedNodeId == null ? fileKey : fileKey + "::" + normalizedNodeId;
    }

    private void collectImageNodeIds(DesignNode node, List<String> result) {
        if (node == null) return;
        if (node.getAsset() != null && node.getAsset().exportAsImage() && hasText(node.getId())) {
            result.add(node.getId());
        }
        if (node.getChildren() != null) {
            for (DesignNode child : node.getChildren()) collectImageNodeIds(child, result);
        }
    }

    private String textOrFallback(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() && !value.asText().isBlank() ? value.asText() : fallback;
    }

    private String normalizeFileKey(String fileKey) {
        if (!hasText(fileKey)) throw new IllegalArgumentException("File Key khong duoc de trong.");
        String normalized = fileKey.trim();
        if (!normalized.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException("File Key Figma khong hop le.");
        }
        return normalized;
    }

    private String normalizeNodeId(String nodeId) {
        if (!hasText(nodeId)) return null;
        String normalized = nodeId.trim().replace('-', ':');
        if (!normalized.matches("[a-zA-Z0-9:_]+")) {
            throw new IllegalArgumentException("Node ID Figma khong hop le.");
        }
        return normalized;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
