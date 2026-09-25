package com.tgnguyen.layoutlybe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgnguyen.layoutlybe.model.snapshot.FigmaSnapshot;
import com.tgnguyen.layoutlybe.model.snapshot.ImportSource;
import com.tgnguyen.layoutlybe.model.snapshot.StoredSnapshotMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SnapshotStorageService {
    private static final String METADATA_FILE = "metadata.json";
    private static final String RAW_FILE = "figma-raw.json";
    private static final String IR_FILE = "design-ir.json";

    private final ObjectMapper objectMapper;
    private final Path snapshotRoot;

    @Autowired
    public SnapshotStorageService(ObjectMapper objectMapper,
                                  @Value("${layoutly.data.directory:layoutly-data}") String dataDirectory) {
        this(objectMapper, Path.of(dataDirectory));
    }

    public SnapshotStorageService(ObjectMapper objectMapper, Path dataDirectory) {
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
        this.snapshotRoot = dataDirectory.toAbsolutePath().normalize().resolve("snapshots");
    }

    public synchronized void save(FigmaSnapshot snapshot, byte[] referenceImage) {
        try {
            Path directory = snapshotDirectory(snapshot.getSnapshotId());
            Files.createDirectories(directory);
            writeString(directory.resolve(RAW_FILE), snapshot.getRawJson());
            writeJson(directory.resolve(IR_FILE), snapshot.getDesignIr());
            if (referenceImage != null && referenceImage.length > 0) {
                writeBytes(directory.resolve("reference.png"), referenceImage);
            }
            writeMetadata(snapshot);
        } catch (Exception exception) {
            throw new RuntimeException("Khong the luu snapshot: " + exception.getMessage(), exception);
        }
    }

    public synchronized void saveAssets(FigmaSnapshot snapshot, AssetExportService.AssetBundle assets) {
        try {
            Path directory = snapshotDirectory(snapshot.getSnapshotId());
            Path assetDirectory = directory.resolve("assets");
            Files.createDirectories(assetDirectory);
            for (Map.Entry<String, byte[]> entry : assets.zipAssetByPath().entrySet()) {
                String fileName = safeAssetName(entry.getKey());
                writeBytes(assetDirectory.resolve(fileName), entry.getValue());
            }
            writeMetadata(snapshot);
        } catch (Exception exception) {
            throw new RuntimeException("Khong the luu asset snapshot: " + exception.getMessage(), exception);
        }
    }

    public Optional<StoredSnapshot> load(String snapshotId) {
        try {
            Path directory = snapshotDirectory(snapshotId);
            Path metadataFile = directory.resolve(METADATA_FILE);
            Path rawFile = directory.resolve(RAW_FILE);
            if (!Files.isRegularFile(metadataFile) || !Files.isRegularFile(rawFile)) return Optional.empty();

            StoredSnapshotMetadata metadata = objectMapper.readValue(
                    metadataFile.toFile(), StoredSnapshotMetadata.class);
            if (!snapshotId.equals(metadata.snapshotId())) return Optional.empty();
            String rawJson = Files.readString(rawFile, StandardCharsets.UTF_8);
            return Optional.of(new StoredSnapshot(metadata, rawJson, loadAssets(directory, metadata)));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RuntimeException("Khong the doc snapshot: " + exception.getMessage(), exception);
        }
    }

    public Optional<String> findLatestRestSnapshotId(String fileKey) {
        if (fileKey == null || fileKey.isBlank() || !Files.isDirectory(snapshotRoot)) return Optional.empty();
        try (var directories = Files.list(snapshotRoot)) {
            return directories
                    .filter(Files::isDirectory)
                    .map(path -> readMetadata(path.resolve(METADATA_FILE)))
                    .flatMap(Optional::stream)
                    .filter(metadata -> metadata.source() == ImportSource.FIGMA_REST)
                    .filter(metadata -> fileKey.equals(metadata.fileKey()))
                    .max((left, right) -> createdAt(left).compareTo(createdAt(right)))
                    .map(StoredSnapshotMetadata::snapshotId);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    public boolean exists(String snapshotId) {
        return Files.isRegularFile(snapshotDirectory(snapshotId).resolve(METADATA_FILE));
    }

    private AssetExportService.AssetBundle loadAssets(Path directory, StoredSnapshotMetadata metadata) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (String storedPath : safeList(metadata.assetFiles())) {
            String fileName = safeAssetName(storedPath);
            Path assetFile = directory.resolve("assets").resolve(fileName).normalize();
            if (!assetFile.startsWith(directory.resolve("assets")) || !Files.isRegularFile(assetFile)) continue;
            try {
                files.put("assets/" + fileName, Files.readAllBytes(assetFile));
            } catch (Exception ignoredUnreadableAsset) {
                // One damaged optional asset must not make the complete design snapshot unreadable.
            }
        }
        return new AssetExportService.AssetBundle(
                metadata.assetPathsByNodeId() == null ? Map.of() : new LinkedHashMap<>(metadata.assetPathsByNodeId()),
                files);
    }

    private void writeMetadata(FigmaSnapshot snapshot) throws Exception {
        AssetExportService.AssetBundle assets = snapshot.getAssets();
        Map<String, String> paths = assets == null ? Map.of() : assets.cssUrlByNodeId();
        List<String> files = assets == null ? List.of() : new ArrayList<>(assets.zipAssetByPath().keySet());
        StoredSnapshotMetadata metadata = new StoredSnapshotMetadata(
                snapshot.getSnapshotId(), snapshot.getSource(), snapshot.getFileKey(), snapshot.getFileName(),
                snapshot.getFigmaVersion(), snapshot.getLastModified(), snapshot.getFetchedAt(),
                snapshot.getPageName(), snapshot.getRootNodeId(), snapshot.getRootNodeName(),
                snapshot.getRootNodeType(), snapshot.getReferenceImagePath(), snapshot.getImageNodeIds(),
                paths, files, snapshot.getWarnings());
        writeJson(snapshotDirectory(snapshot.getSnapshotId()).resolve(METADATA_FILE), metadata);
    }

    private Optional<StoredSnapshotMetadata> readMetadata(Path path) {
        if (!Files.isRegularFile(path)) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(path.toFile(), StoredSnapshotMetadata.class));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Instant createdAt(StoredSnapshotMetadata metadata) {
        return metadata.createdAt() == null ? Instant.EPOCH : metadata.createdAt();
    }

    private Path snapshotDirectory(String snapshotId) {
        if (snapshotId == null || !snapshotId.matches("[a-zA-Z0-9_-]{8,80}")) {
            throw new IllegalArgumentException("Snapshot ID khong hop le.");
        }
        Path directory = snapshotRoot.resolve(snapshotId).normalize();
        if (!directory.startsWith(snapshotRoot)) {
            throw new IllegalArgumentException("Snapshot ID khong hop le.");
        }
        return directory;
    }

    private String safeAssetName(String path) {
        if (path == null || path.isBlank() || path.contains("..") || path.contains("\\")) {
            throw new IllegalArgumentException("Ten asset khong hop le.");
        }
        String fileName = Path.of(path).getFileName().toString();
        if (!fileName.equals(path) && !("assets/" + fileName).equals(path)) {
            throw new IllegalArgumentException("Ten asset khong hop le.");
        }
        if (!fileName.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,159}")) {
            throw new IllegalArgumentException("Ten asset khong hop le.");
        }
        return fileName;
    }

    private void writeJson(Path destination, Object value) throws Exception {
        byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
        writeBytes(destination, bytes);
    }

    private void writeString(Path destination, String content) throws Exception {
        writeBytes(destination, content.getBytes(StandardCharsets.UTF_8));
    }

    private void writeBytes(Path destination, byte[] content) throws Exception {
        Files.createDirectories(destination.getParent());
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        Files.write(temporary, content);
        try {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ignoredAtomicMove) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    public record StoredSnapshot(
            StoredSnapshotMetadata metadata,
            String rawJson,
            AssetExportService.AssetBundle assets
    ) {
    }
}
