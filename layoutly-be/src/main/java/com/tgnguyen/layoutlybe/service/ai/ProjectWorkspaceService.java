package com.tgnguyen.layoutlybe.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgnguyen.layoutlybe.model.ir.DesignNode;
import com.tgnguyen.layoutlybe.service.AssetExportService;
import com.tgnguyen.layoutlybe.service.HtmlProjectExportService;
import com.tgnguyen.layoutlybe.service.ReactProjectExportService;
import com.tgnguyen.layoutlybe.service.SnapshotStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ProjectWorkspaceService {

    private final Path workspacesRoot;
    private final SnapshotStorageService snapshotStorageService;
    private final ReactProjectExportService reactProjectExportService;
    private final HtmlProjectExportService htmlProjectExportService;
    private final WorkspacePathGuard pathGuard;
    private final ObjectMapper objectMapper;

    @Autowired
    public ProjectWorkspaceService(
            ObjectMapper objectMapper,
            SnapshotStorageService snapshotStorageService,
            ReactProjectExportService reactProjectExportService,
            HtmlProjectExportService htmlProjectExportService,
            WorkspacePathGuard pathGuard,
            @Value("${layoutly.data.directory:layoutly-data}") String dataDirectory) {
        this.objectMapper = objectMapper.copy()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.snapshotStorageService = snapshotStorageService;
        this.reactProjectExportService = reactProjectExportService;
        this.htmlProjectExportService = htmlProjectExportService;
        this.pathGuard = pathGuard;
        this.workspacesRoot = Path.of(dataDirectory).toAbsolutePath().normalize().resolve("workspaces");
    }

    public Path getProjectDirectory(String snapshotId) {
        validateId(snapshotId, "Snapshot ID");
        return workspacesRoot.resolve(snapshotId).normalize();
    }

    public synchronized Path getOrInitializeWorkspace(String snapshotId, String revisionId, String targetFramework) {
        Path projectDir = getProjectDirectory(snapshotId);

        String safeFramework = (targetFramework == null || targetFramework.isBlank()) ? "REACT" : targetFramework.toUpperCase();
        Path revisionDir = projectDir.resolve("revisions").resolve(revisionId == null || revisionId.isBlank() ? "rev_000_baseline" : revisionId).normalize();

        if (!Files.isDirectory(revisionDir)) {
            Path acceptedDir = projectDir.resolve("accepted");
            Path baselineDir = projectDir.resolve("revisions").resolve("rev_000_baseline");

            if (!Files.isDirectory(baselineDir)) {
                generateBaseline(snapshotId, baselineDir, safeFramework);
            }

            if (!revisionDir.equals(baselineDir)) {
                Path sourceDir = Files.isDirectory(acceptedDir) ? acceptedDir : baselineDir;
                copyDirectory(sourceDir, revisionDir);
            }
        }

        return revisionDir;
    }

    public Path getAcceptedWorkspaceDir(String snapshotId, String targetFramework) {
        validateId(snapshotId, "Snapshot ID");
        Path projectDir = workspacesRoot.resolve(snapshotId).normalize();
        Path acceptedDir = projectDir.resolve("accepted");

        if (!Files.isDirectory(acceptedDir)) {
            return getOrInitializeWorkspace(snapshotId, "rev_000_baseline", targetFramework);
        }

        return acceptedDir;
    }

    public void updateAcceptedWorkspace(String snapshotId, Path sourceRevisionDir) {
        validateId(snapshotId, "Snapshot ID");
        Path projectDir = workspacesRoot.resolve(snapshotId).normalize();
        Path acceptedDir = projectDir.resolve("accepted");

        try {
            deleteDirectoryIfExists(acceptedDir);
            copyDirectory(sourceRevisionDir, acceptedDir);
        } catch (Exception e) {
            throw new RuntimeException("Khong the cap nhat accepted workspace: " + e.getMessage(), e);
        }
    }

    public List<String> listProjectFiles(Path workspaceDir) {
        List<String> result = new ArrayList<>();
        try (var stream = Files.walk(workspaceDir)) {
            stream.filter(Files::isRegularFile)
                    .forEach(file -> {
                        String relative = workspaceDir.relativize(file).toString().replace('\\', '/');
                        if (pathGuard.isSafeRelativePath(relative)) {
                            result.add(relative);
                        }
                    });
        } catch (Exception e) {
            throw new RuntimeException("Loi khi liet ke files trong workspace: " + e.getMessage(), e);
        }
        result.sort(String::compareTo);
        return result;
    }

    public String readProjectFile(Path workspaceDir, String relativePath) {
        Path file = pathGuard.validateAndResolve(workspaceDir, relativePath);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("File khong ton tai hoac khong phai file thuong: " + relativePath);
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Loi khi doc file " + relativePath + ": " + e.getMessage(), e);
        }
    }

    public void writeProjectFile(Path workspaceDir, String relativePath, String content) {
        Path file = pathGuard.validateAndResolve(workspaceDir, relativePath);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content == null ? "" : content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Loi khi ghi file " + relativePath + ": " + e.getMessage(), e);
        }
    }

    public boolean deleteProjectFile(Path workspaceDir, String relativePath) {
        Path file = pathGuard.validateAndResolve(workspaceDir, relativePath);
        try {
            return Files.deleteIfExists(file);
        } catch (Exception e) {
            throw new RuntimeException("Loi khi xoa file " + relativePath + ": " + e.getMessage(), e);
        }
    }

    public Map<String, List<String>> searchProjectCode(Path workspaceDir, String query) {
        if (query == null || query.isBlank()) return Map.of();
        String lowerQuery = query.toLowerCase();

        Map<String, List<String>> matches = new LinkedHashMap<>();
        List<String> files = listProjectFiles(workspaceDir);

        for (String relative : files) {
            try {
                Path file = pathGuard.validateAndResolve(workspaceDir, relative);
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                List<String> matchingLines = new ArrayList<>();
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).toLowerCase().contains(lowerQuery)) {
                        matchingLines.add((i + 1) + ": " + lines.get(i).trim());
                    }
                }
                if (!matchingLines.isEmpty()) {
                    matches.put(relative, matchingLines);
                }
            } catch (Exception ignored) {
            }
        }
        return matches;
    }

    public Map<String, byte[]> exportWorkspaceAsZipMap(Path workspaceDir) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (var stream = Files.walk(workspaceDir)) {
            stream.filter(Files::isRegularFile)
                    .forEach(file -> {
                        String relative = workspaceDir.relativize(file).toString().replace('\\', '/');
                        if (pathGuard.isSafeRelativePath(relative)) {
                            try {
                                files.put(relative, Files.readAllBytes(file));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });
        } catch (Exception e) {
            throw new RuntimeException("Loi dong goi workspace ZIP: " + e.getMessage(), e);
        }
        return files;
    }

    private void generateBaseline(String snapshotId, Path destinationDir, String framework) {
        SnapshotStorageService.StoredSnapshot stored = snapshotStorageService.load(snapshotId)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay snapshot ID: " + snapshotId));

        try {
            Files.createDirectories(destinationDir);
            DesignNode designIr = readIr(snapshotId);
            AssetExportService.AssetBundle assets = stored.assets() != null ? stored.assets() : new AssetExportService.AssetBundle(Map.of(), Map.of());

            Map<String, byte[]> fileMap;
            if ("HTML".equalsIgnoreCase(framework)) {
                fileMap = buildHtmlProject(designIr, assets);
            } else {
                fileMap = reactProjectExportService.buildProject(designIr, assets);
            }

            for (Map.Entry<String, byte[]> entry : fileMap.entrySet()) {
                Path file = destinationDir.resolve(entry.getKey()).normalize();
                Files.createDirectories(file.getParent());
                Files.write(file, entry.getValue());
            }

            // Also copy into accepted/
            Path acceptedDir = destinationDir.getParent().getParent().resolve("accepted");
            deleteDirectoryIfExists(acceptedDir);
            copyDirectory(destinationDir, acceptedDir);

        } catch (Exception e) {
            throw new RuntimeException("Loi khi tao baseline workspace cho snapshot " + snapshotId + ": " + e.getMessage(), e);
        }
    }

    private Map<String, byte[]> buildHtmlProject(DesignNode root, AssetExportService.AssetBundle assets) {
        Map<String, byte[]> fileMap = new LinkedHashMap<>();
        byte[] zipBytes = htmlProjectExportService.export(root, assets, "AUTO");

        try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            java.util.zip.ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    int len;
                    while ((len = zip.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                    fileMap.put(entry.getName(), baos.toByteArray());
                }
                zip.closeEntry();
            }
        } catch (Exception e) {
            throw new RuntimeException("Loi parse HTML project files: " + e.getMessage(), e);
        }
        return fileMap;
    }

    private DesignNode readIr(String snapshotId) throws Exception {
        Path irFile = snapshotStorageService.load(snapshotId)
                .map(s -> workspacesRoot.getParent().resolve("snapshots").resolve(snapshotId).resolve("design-ir.json"))
                .orElseThrow();
        return objectMapper.readValue(irFile.toFile(), DesignNode.class);
    }

    private void copyDirectory(Path source, Path target) {
        try {
            Files.createDirectories(target);
            try (var stream = Files.walk(source)) {
                stream.forEach(src -> {
                    try {
                        Path rel = source.relativize(src);
                        Path dest = target.resolve(rel);
                        if (Files.isDirectory(src)) {
                            Files.createDirectories(dest);
                        } else {
                            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        } catch (Exception e) {
            throw new RuntimeException("Loi sao chep thu muc workspace: " + e.getMessage(), e);
        }
    }

    private void deleteDirectoryIfExists(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException ignored) {}
                    });
        }
    }

    private void validateId(String id, String name) {
        if (id == null || !id.matches("[a-zA-Z0-9_-]{8,80}")) {
            throw new IllegalArgumentException(name + " khong hop le.");
        }
    }
}
