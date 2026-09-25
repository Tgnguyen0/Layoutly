package com.tgnguyen.layoutlybe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgnguyen.layoutlybe.dto.SnapshotResponse;
import com.tgnguyen.layoutlybe.model.snapshot.FigmaSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Service
public class FixtureService {
    private final ObjectMapper objectMapper;
    private final DesignStructureService structureService;
    private final Path fixtureRoot;

    public FixtureService(ObjectMapper objectMapper,
                          DesignStructureService structureService,
                          @Value("${layoutly.fixtures.directory:test-fixtures}") String fixtureDirectory) {
        this.objectMapper = objectMapper;
        this.structureService = structureService;
        this.fixtureRoot = Path.of(fixtureDirectory).toAbsolutePath().normalize();
    }

    public Path save(String caseName, FigmaSnapshot snapshot, AssetExportService.AssetBundle assets) {
        String safeName = sanitizeCaseName(caseName);
        Path caseDirectory = fixtureRoot.resolve(safeName).normalize();
        if (!caseDirectory.startsWith(fixtureRoot)) {
            throw new IllegalArgumentException("Ten test case khong hop le.");
        }

        try {
            Path assetDirectory = caseDirectory.resolve("assets");
            Files.createDirectories(assetDirectory);
            SnapshotResponse metadata = SnapshotResponse.from(
                    snapshot,
                    structureService.analyze(snapshot.getDesignIr()));
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(caseDirectory.resolve("metadata.json").toFile(), metadata);
            Files.writeString(caseDirectory.resolve("figma-raw.json"), snapshot.getRawJson(), StandardCharsets.UTF_8);
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(caseDirectory.resolve("design-ir.json").toFile(), snapshot.getDesignIr());

            for (Map.Entry<String, byte[]> entry : assets.zipAssetByPath().entrySet()) {
                String fileName = Path.of(entry.getKey()).getFileName().toString();
                Files.write(assetDirectory.resolve(fileName), entry.getValue());
            }
            return caseDirectory;
        } catch (Exception exception) {
            throw new RuntimeException("Khong the luu test fixture: " + exception.getMessage(), exception);
        }
    }

    private String sanitizeCaseName(String caseName) {
        if (caseName == null || caseName.isBlank()) {
            throw new IllegalArgumentException("Ten test case khong duoc de trong.");
        }
        String safe = caseName.trim().toLowerCase()
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("(^-|-$)", "");
        if (safe.isBlank()) throw new IllegalArgumentException("Ten test case khong hop le.");
        return safe;
    }
}
