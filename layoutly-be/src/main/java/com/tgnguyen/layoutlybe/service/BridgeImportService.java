package com.tgnguyen.layoutlybe.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgnguyen.layoutlybe.dto.BridgeImportMetadata;
import com.tgnguyen.layoutlybe.dto.BridgeImportResponse;
import com.tgnguyen.layoutlybe.exception.PayloadTooLargeException;
import com.tgnguyen.layoutlybe.model.ir.DesignNode;
import com.tgnguyen.layoutlybe.model.snapshot.FigmaSnapshot;
import com.tgnguyen.layoutlybe.model.snapshot.ImportSource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class BridgeImportService {
    static final long MAX_METADATA_BYTES = 256 * 1024;
    static final long MAX_DESIGN_BYTES = 50L * 1024 * 1024;
    static final long MAX_REFERENCE_BYTES = 15L * 1024 * 1024;
    static final long MAX_ASSET_BYTES = 12L * 1024 * 1024;
    static final long MAX_TOTAL_BYTES = 100L * 1024 * 1024;
    static final int MAX_ASSET_COUNT = 500;

    private static final Set<String> ASSET_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp", "image/svg+xml");

    private final ObjectMapper objectMapper;
    private final FigmaParserService parserService;
    private final FigmaSnapshotService snapshotService;
    private final DesignStructureService structureService;

    public BridgeImportService(ObjectMapper objectMapper,
                               FigmaParserService parserService,
                               FigmaSnapshotService snapshotService,
                               DesignStructureService structureService) {
        this.objectMapper = objectMapper;
        this.parserService = parserService;
        this.snapshotService = snapshotService;
        this.structureService = structureService;
    }

    public BridgeImportResponse importSnapshot(MultipartFile metadataPart,
                                               MultipartFile designPart,
                                               MultipartFile referenceImage,
                                               List<MultipartFile> assetParts) {
        requirePart(metadataPart, "metadata");
        requirePart(designPart, "designJson");
        enforceSize(metadataPart, MAX_METADATA_BYTES, "metadata");
        enforceSize(designPart, MAX_DESIGN_BYTES, "designJson");
        validateJsonContentType(metadataPart, "metadata");
        validateJsonContentType(designPart, "designJson");

        List<MultipartFile> assets = assetParts == null ? List.of() : assetParts;
        if (assets.size() > MAX_ASSET_COUNT) {
            throw new IllegalArgumentException("Bridge payload co qua nhieu asset (toi da 500).");
        }
        long totalSize = metadataPart.getSize() + designPart.getSize();
        if (referenceImage != null && !referenceImage.isEmpty()) {
            enforceSize(referenceImage, MAX_REFERENCE_BYTES, "referenceImage");
            validateImageContentType(referenceImage, true);
            totalSize += referenceImage.getSize();
        }
        for (MultipartFile asset : assets) {
            enforceSize(asset, MAX_ASSET_BYTES, "asset");
            validateImageContentType(asset, false);
            totalSize += asset.getSize();
        }
        if (totalSize > MAX_TOTAL_BYTES) {
            throw new PayloadTooLargeException("Bridge payload vuot qua gioi han 100 MB.");
        }

        try {
            BridgeImportMetadata metadata = objectMapper.readValue(
                    metadataPart.getBytes(), BridgeImportMetadata.class);
            if (metadata.source() != ImportSource.FIGMA_PLUGIN) {
                throw new IllegalArgumentException("Bridge import chi chap nhan source FIGMA_PLUGIN.");
            }

            String rawJson = new String(designPart.getBytes(), StandardCharsets.UTF_8);
            JsonNode rawDesign = objectMapper.readTree(rawJson);
            if (rawDesign == null || !rawDesign.isObject() || !rawDesign.hasNonNull("document")) {
                throw new IllegalArgumentException("designJson khong co document hop le.");
            }
            DesignNode designIr = parserService.parseDesignTree(rawJson);
            AssetExportService.AssetBundle bundle = buildAssets(metadata, assets);

            List<String> warnings = new ArrayList<>(safeList(metadata.warnings()));
            byte[] referenceBytes = null;
            if (referenceImage != null && !referenceImage.isEmpty()) {
                referenceBytes = referenceImage.getBytes();
            } else if (!"CANVAS".equalsIgnoreCase(metadata.nodeType())
                    && warnings.stream().noneMatch(value -> value.toLowerCase(Locale.ROOT).contains("reference"))) {
                warnings.add("Reference image was not included by the plugin.");
            }

            Instant createdAt = parseInstant(metadata.exportedAt());
            String snapshotId = UUID.randomUUID().toString();
            String fileKey = safeIdentifier(metadata.fileKey(), "bridge-" + snapshotId.substring(0, 8));
            List<String> imageNodeIds = new ArrayList<>(bundle.cssUrlByNodeId().keySet());
            FigmaSnapshot snapshot = new FigmaSnapshot(
                    snapshotId,
                    ImportSource.FIGMA_PLUGIN,
                    fileKey,
                    textOr(metadata.fileName(), "Figma Plugin Import"),
                    "bridge-" + createdAt.toEpochMilli(),
                    metadata.exportedAt(),
                    createdAt,
                    metadata.pageName(),
                    textOr(metadata.nodeId(), designIr.getId()),
                    textOr(metadata.nodeName(), designIr.getName()),
                    textOr(metadata.nodeType(), designIr.getType()),
                    referenceBytes == null ? null : "reference.png",
                    warnings,
                    rawJson,
                    designIr,
                    imageNodeIds);
            snapshotService.storeImportedSnapshot(snapshot, bundle, referenceBytes);

            int nodeCount = structureService.analyze(designIr).nodeCount();
            return new BridgeImportResponse(
                    snapshotId, snapshot.getFileName(), snapshot.getRootNodeName(), snapshot.getSource(),
                    nodeCount, snapshot.getFetchedAt(), snapshot.getWarnings());
        } catch (IllegalArgumentException | PayloadTooLargeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Bridge payload khong hop le: " + exception.getMessage(), exception);
        }
    }

    private AssetExportService.AssetBundle buildAssets(BridgeImportMetadata metadata,
                                                        List<MultipartFile> assetParts) throws Exception {
        Map<String, BridgeImportMetadata.BridgeAssetDescriptor> descriptors = new HashMap<>();
        for (BridgeImportMetadata.BridgeAssetDescriptor descriptor : safeList(metadata.assets())) {
            if (descriptor == null || descriptor.nodeId() == null || descriptor.nodeId().isBlank()) {
                throw new IllegalArgumentException("Asset descriptor thieu nodeId.");
            }
            String fileName = validateFileName(descriptor.fileName());
            if (descriptors.putIfAbsent(fileName, descriptor) != null) {
                throw new IllegalArgumentException("Asset filename bi trung: " + fileName);
            }
        }

        Map<String, String> cssPaths = new LinkedHashMap<>();
        Map<String, byte[]> files = new LinkedHashMap<>();
        Set<String> uploaded = new LinkedHashSet<>();
        for (MultipartFile asset : assetParts) {
            String fileName = validateFileName(asset.getOriginalFilename());
            if (!uploaded.add(fileName)) {
                throw new IllegalArgumentException("Asset filename bi trung: " + fileName);
            }
            BridgeImportMetadata.BridgeAssetDescriptor descriptor = descriptors.get(fileName);
            if (descriptor == null) {
                throw new IllegalArgumentException("Khong tim thay descriptor cho asset: " + fileName);
            }
            if (descriptor.contentType() != null && asset.getContentType() != null
                    && !descriptor.contentType().equalsIgnoreCase(asset.getContentType())) {
                throw new IllegalArgumentException("Content type asset khong khop metadata: " + fileName);
            }
            String path = "assets/" + fileName;
            cssPaths.put(descriptor.nodeId(), path);
            files.put(path, asset.getBytes());
        }
        if (!uploaded.containsAll(descriptors.keySet())) {
            throw new IllegalArgumentException("Metadata khai bao asset nhung khong co file upload.");
        }
        return new AssetExportService.AssetBundle(cssPaths, files);
    }

    private void requirePart(MultipartFile part, String name) {
        if (part == null || part.isEmpty()) throw new IllegalArgumentException("Thieu multipart part: " + name);
    }

    private void enforceSize(MultipartFile part, long maximum, String name) {
        if (part != null && part.getSize() > maximum) {
            throw new PayloadTooLargeException(name + " vuot qua gioi han cho phep.");
        }
    }

    private void validateJsonContentType(MultipartFile part, String name) {
        String contentType = part.getContentType();
        if (contentType != null && !contentType.equals("application/json")
                && !contentType.equals("application/octet-stream")) {
            throw new IllegalArgumentException(name + " phai co content type application/json.");
        }
    }

    private void validateImageContentType(MultipartFile part, boolean reference) {
        String fileName = validateFileName(part.getOriginalFilename());
        String contentType = part.getContentType();
        if (contentType != null && !contentType.equals("application/octet-stream")
                && !ASSET_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Content type asset khong duoc ho tro: " + contentType);
        }
        if (reference && !fileName.toLowerCase(Locale.ROOT).endsWith(".png")) {
            throw new IllegalArgumentException("Reference image phai la file PNG.");
        }
    }

    private String validateFileName(String value) {
        if (value == null || value.isBlank() || value.contains("..") || value.contains("/")
                || value.contains("\\") || value.contains(":")) {
            throw new IllegalArgumentException("Ten asset khong hop le.");
        }
        if (!value.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,159}")) {
            throw new IllegalArgumentException("Ten asset khong hop le: " + value);
        }
        return value;
    }

    private String safeIdentifier(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String safe = value.trim().replaceAll("[^a-zA-Z0-9_-]+", "-").replaceAll("(^-|-$)", "");
        return safe.isBlank() ? fallback : safe;
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return Instant.now();
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return Instant.now();
        }
    }

    private String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
