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
    private final Map<String, Map<String, String>> previewUrlsByKey = new ConcurrentHashMap<>();
    private final Map<String, Mono<Map<String, String>>> previewUrlFlights = new ConcurrentHashMap<>();

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
        String normalizedKey = normalizeFileKey(fileKey);
        Optional<FigmaSnapshot> cached = findByFileKey(normalizedKey);
        if (cached.isPresent()) {
            rememberToken(cached.get().getSnapshotId(), token);
            return Mono.just(cached.get());
        }

        return importFlights.computeIfAbsent(normalizedKey, key ->
                figmaService.getFile(key, token)
                        .map(rawJson -> createSnapshot(UUID.randomUUID().toString(), key, rawJson))
                        .doOnNext(snapshot -> storeSnapshot(snapshot, token))
                        .doFinally(signal -> importFlights.remove(key))
                        .cache());
    }

    public Mono<FigmaSnapshot> getOrImportByFileKey(String fileKey, String token) {
        return importSnapshot(fileKey, token);
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
                        .map(rawJson -> createSnapshot(id, current.getFileKey(), rawJson))
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
            snapshotIdByFileKey.put(loaded.getFileKey(), loaded.getSnapshotId());
        }
        return loaded;
    }

    public Optional<FigmaSnapshot> findByFileKey(String fileKey) {
        String normalizedKey = normalizeFileKey(fileKey);
        String snapshotId = snapshotIdByFileKey.get(normalizedKey);
        if (snapshotId != null) return Optional.of(requireSnapshot(snapshotId));
        return storageService.findLatestRestSnapshotId(normalizedKey).map(this::requireSnapshot);
    }

    public Mono<AssetExportService.AssetBundle> getAssets(String snapshotId) {
        FigmaSnapshot snapshot = requireSnapshot(snapshotId);
        if (snapshot.getAssets() != null) return Mono.just(snapshot.getAssets());
        if (snapshot.getSource() == ImportSource.FIGMA_PLUGIN) {
            return Mono.just(new AssetExportService.AssetBundle(Map.of(), Map.of()));
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
        if (snapshot.getAssets() != null) {
            return Mono.just(toLocalPreviewUrls(snapshot));
        }
        if (snapshot.getSource() == ImportSource.FIGMA_PLUGIN) return Mono.just(Map.of());

        String cacheKey = previewCacheKey(snapshotId, previewRoot);
        Map<String, String> cached = previewUrlsByKey.get(cacheKey);
        if (cached != null) return Mono.just(cached);

        return previewUrlFlights.computeIfAbsent(cacheKey, key ->
                assetExportService.getPreviewImageUrls(
                                snapshot.getFileKey(),
                                tokenBySnapshotId.get(snapshotId),
                                previewRoot)
                        .doOnNext(urls -> previewUrlsByKey.put(key, Map.copyOf(urls)))
                        .doFinally(signal -> previewUrlFlights.remove(key))
                        .cache());
    }

    public Map<String, String> getLocalAssetUrls(String snapshotId) {
        FigmaSnapshot snapshot = requireSnapshot(snapshotId);
        return snapshot.getAssets() == null ? Map.of() : toLocalPreviewUrls(snapshot);
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
        try {
            JsonNode response = objectMapper.readTree(rawJson);
            DesignNode designIr = parserService.parseDesignTree(rawJson);
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
                    designIr.getId(),
                    designIr.getName(),
                    designIr.getType(),
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
        clearPreviewCache(snapshot.getSnapshotId());
        snapshotsById.put(snapshot.getSnapshotId(), snapshot);
        if (snapshot.getSource() == ImportSource.FIGMA_REST && hasText(snapshot.getFileKey())) {
            snapshotIdByFileKey.put(snapshot.getFileKey(), snapshot.getSnapshotId());
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

    private String previewCacheKey(String snapshotId, DesignNode previewRoot) {
        String nodeId = previewRoot == null || !hasText(previewRoot.getId()) ? "root" : previewRoot.getId();
        return snapshotId + "::" + nodeId;
    }

    private void clearPreviewCache(String snapshotId) {
        String prefix = snapshotId + "::";
        previewUrlsByKey.keySet().removeIf(key -> key.startsWith(prefix));
        previewUrlFlights.keySet().removeIf(key -> key.startsWith(prefix));
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
