package com.tgnguyen.layoutlybe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgnguyen.layoutlybe.controller.SnapshotController;
import com.tgnguyen.layoutlybe.dto.BridgeImportResponse;
import com.tgnguyen.layoutlybe.model.snapshot.ImportSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BridgeImportServiceTest {
    @TempDir
    Path tempDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final FigmaService figmaService = mock(FigmaService.class);
    private final AssetExportService assetExportService = mock(AssetExportService.class);
    private final FixtureService fixtureService = mock(FixtureService.class);
    private FigmaParserService parserService;
    private SnapshotStorageService storageService;
    private FigmaSnapshotService snapshotService;
    private BridgeImportService bridgeImportService;
    private SnapshotController snapshotController;

    @BeforeEach
    void setUp() {
        parserService = new FigmaParserService();
        storageService = new SnapshotStorageService(objectMapper, tempDirectory);
        snapshotService = new FigmaSnapshotService(
                figmaService, parserService, assetExportService, objectMapper, storageService);
        DesignStructureService structureService = new DesignStructureService();
        bridgeImportService = new BridgeImportService(
                objectMapper, parserService, snapshotService, structureService);
        HtmlGeneratorService html = new HtmlGeneratorService();
        CssGeneratorService css = new CssGeneratorService();
        ReactGeneratorService react = new ReactGeneratorService();
        snapshotController = new SnapshotController(
                snapshotService, structureService, html, css, react,
                new HtmlProjectExportService(html, css),
                new ReactProjectExportService(react, css), fixtureService);
    }

    @Test
    void bridgeImportNeedsNoTokenAndPersistsCompleteSnapshot() throws Exception {
        BridgeImportResponse response = importBridge();

        assertThat(response.source()).isEqualTo(ImportSource.FIGMA_PLUGIN);
        assertThat(response.nodeName()).isEqualTo("Landing Frame");
        assertThat(response.nodeCount()).isEqualTo(4);
        Path snapshotDirectory = tempDirectory.resolve("snapshots").resolve(response.snapshotId());
        assertThat(snapshotDirectory.resolve("metadata.json")).isRegularFile();
        assertThat(snapshotDirectory.resolve("figma-raw.json")).isRegularFile();
        assertThat(snapshotDirectory.resolve("design-ir.json")).isRegularFile();
        assertThat(snapshotDirectory.resolve("reference.png")).isRegularFile();
        assertThat(snapshotDirectory.resolve("assets/icon.svg")).isRegularFile();
        assertThat(Files.readString(snapshotDirectory.resolve("figma-raw.json")))
                .contains("Landing Frame");
        verifyNoInteractions(figmaService);
    }

    @Test
    void allBridgeGenerationOperationsUseOnlyLocalSnapshotData() throws Exception {
        BridgeImportResponse imported = importBridge();
        String snapshotId = imported.snapshotId();

        assertThat(snapshotController.getHtml(snapshotId)).contains("Landing Frame");
        assertThat(snapshotController.getCss(snapshotId))
                .contains(".node-2-1")
                .contains("/api/snapshots/" + snapshotId + "/assets/icon.svg");
        assertThat(snapshotController.getReact(snapshotId)).contains("function LandingFrame");
        assertThat(snapshotController.getPreview(snapshotId, 1440).block())
                .contains("/api/snapshots/" + snapshotId + "/assets/icon.svg");
        assertThat(snapshotController.getAsset(snapshotId, "icon.svg").block().getBody())
                .isEqualTo("<svg/>".getBytes(StandardCharsets.UTF_8));

        byte[] htmlZip = snapshotController.export(snapshotId, "HTML", "AUTO").block().getBody();
        byte[] reactZip = snapshotController.export(snapshotId, "REACT", "AUTO").block().getBody();
        assertThat(zipEntries(htmlZip)).contains("styles.css", "assets/icon.svg");
        assertThat(zipEntries(reactZip)).contains("src/styles/layoutly.css", "src/assets/icon.svg");
        verifyNoInteractions(figmaService, assetExportService);
    }

    @Test
    void bridgeSnapshotReloadsAfterMemoryIsLostWithoutFigmaCalls() throws Exception {
        BridgeImportResponse imported = importBridge();
        FigmaService restartedFigmaService = mock(FigmaService.class);
        AssetExportService restartedAssetService = mock(AssetExportService.class);
        FigmaSnapshotService restarted = new FigmaSnapshotService(
                restartedFigmaService, parserService, restartedAssetService, objectMapper,
                new SnapshotStorageService(objectMapper, tempDirectory));

        var loaded = restarted.requireSnapshot(imported.snapshotId());
        assertThat(loaded.getSource()).isEqualTo(ImportSource.FIGMA_PLUGIN);
        assertThat(loaded.getRawJson()).contains("Bridge Test");
        assertThat(restarted.getAssets(imported.snapshotId()).block().zipAssetByPath())
                .containsKey("assets/icon.svg");
        assertThat(restarted.getPreviewImageUrls(imported.snapshotId(), loaded.getDesignIr()).block())
                .containsValue("/api/snapshots/" + imported.snapshotId() + "/assets/icon.svg");
        verifyNoInteractions(restartedFigmaService, restartedAssetService);
    }

    @Test
    void bridgeRejectsInvalidJsonAndPathTraversalAsset() throws Exception {
        MockMultipartFile badJson = new MockMultipartFile(
                "designJson", "design.json", "application/json", "not-json".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> bridgeImportService.importSnapshot(
                metadata("icon.svg"), badJson, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bridge payload");

        MockMultipartFile traversal = new MockMultipartFile(
                "assets", "../icon.svg", "image/svg+xml", "<svg/>".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> bridgeImportService.importSnapshot(
                metadata("../icon.svg"), design(), null, List.of(traversal)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ten asset");
        verifyNoInteractions(figmaService);
    }

    @Test
    void existingRestImportStillUsesFigmaService() {
        String rawJson = rawDesign();
        when(figmaService.getFile(eq("rest-file"), any())).thenReturn(Mono.just(rawJson));

        var imported = snapshotController.importFigma(
                new SnapshotController.ImportRequest("rest-file", null), "token").block();

        assertThat(imported).isNotNull();
        assertThat(imported.source()).isEqualTo(ImportSource.FIGMA_REST);
        verify(figmaService).getFile("rest-file", "token");
    }

    private BridgeImportResponse importBridge() throws Exception {
        MultipartFile reference = new MockMultipartFile(
                "referenceImage", "reference.png", "image/png", new byte[]{1, 2, 3});
        MultipartFile asset = new MockMultipartFile(
                "assets", "icon.svg", "image/svg+xml", "<svg/>".getBytes(StandardCharsets.UTF_8));
        return bridgeImportService.importSnapshot(
                metadata("icon.svg"), design(), reference, List.of(asset));
    }

    private MockMultipartFile metadata(String assetFileName) throws Exception {
        Map<String, Object> metadata = Map.of(
                "source", "FIGMA_PLUGIN",
                "fileKey", "bridge-file",
                "fileName", "Bridge Test",
                "pageName", "Home",
                "nodeId", "2:1",
                "nodeName", "Landing Frame",
                "nodeType", "FRAME",
                "exportedAt", "2026-09-25T05:00:00Z",
                "assets", List.of(Map.of(
                        "nodeId", "3:1", "fileName", assetFileName, "contentType", "image/svg+xml")),
                "warnings", List.of());
        return new MockMultipartFile(
                "metadata", "metadata.json", "application/json", objectMapper.writeValueAsBytes(metadata));
    }

    private MockMultipartFile design() {
        return new MockMultipartFile(
                "designJson", "design.json", "application/json", rawDesign().getBytes(StandardCharsets.UTF_8));
    }

    private String rawDesign() {
        return """
                {
                  "name":"Bridge Test","version":"bridge-v1","document":{
                    "id":"0:0","name":"Document","type":"DOCUMENT","children":[{
                      "id":"1:0","name":"Home","type":"CANVAS","children":[{
                        "id":"2:1","name":"Landing Frame","type":"FRAME",
                        "absoluteBoundingBox":{"x":0,"y":0,"width":1440,"height":900},
                        "children":[{
                          "id":"3:1","name":"Icon","type":"VECTOR",
                          "absoluteBoundingBox":{"x":20,"y":20,"width":32,"height":32}
                        }]
                      }]
                    }]
                  }
                }
                """;
    }

    private List<String> zipEntries(byte[] bytes) throws Exception {
        List<String> names = new java.util.ArrayList<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                names.add(entry.getName());
            }
        }
        return names;
    }
}
