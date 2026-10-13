package com.tgnguyen.layoutlybe.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgnguyen.layoutlybe.model.ai.AiChatRequest;
import com.tgnguyen.layoutlybe.model.ai.AiChatResponse;
import com.tgnguyen.layoutlybe.model.ai.CodeDiff;
import com.tgnguyen.layoutlybe.model.ai.RevisionInfo;
import com.tgnguyen.layoutlybe.model.ir.DesignNode;
import com.tgnguyen.layoutlybe.model.snapshot.FigmaSnapshot;
import com.tgnguyen.layoutlybe.model.snapshot.ImportSource;
import com.tgnguyen.layoutlybe.service.AssetExportService;
import com.tgnguyen.layoutlybe.service.CssGeneratorService;
import com.tgnguyen.layoutlybe.service.FigmaParserService;
import com.tgnguyen.layoutlybe.service.HtmlGeneratorService;
import com.tgnguyen.layoutlybe.service.HtmlProjectExportService;
import com.tgnguyen.layoutlybe.service.ReactGeneratorService;
import com.tgnguyen.layoutlybe.service.ReactProjectExportService;
import com.tgnguyen.layoutlybe.service.SnapshotStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class AiAgentEndToEndTest {

    private ProjectWorkspaceService workspaceService;
    private RevisionManager revisionManager;
    private WorkspaceToolRegistry toolRegistry;
    private SandboxedExecutionService sandboxedExecutionService;
    private SnapshotStorageService snapshotStorageService;
    private CodePatchEngine patchEngine;
    private String snapshotId = "snap_e2e_test_99999";

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        snapshotStorageService = new SnapshotStorageService(objectMapper, tempDir);
        WorkspacePathGuard pathGuard = new WorkspacePathGuard();
        patchEngine = new CodePatchEngine();
        sandboxedExecutionService = new SandboxedExecutionService(pathGuard);

        CssGeneratorService cssGeneratorService = new CssGeneratorService();
        ReactGeneratorService reactGeneratorService = new ReactGeneratorService();
        ReactProjectExportService reactExportService = new ReactProjectExportService(reactGeneratorService, cssGeneratorService);
        HtmlGeneratorService htmlGeneratorService = new HtmlGeneratorService();
        HtmlProjectExportService htmlExportService = new HtmlProjectExportService(htmlGeneratorService, cssGeneratorService);

        workspaceService = new ProjectWorkspaceService(
                objectMapper,
                snapshotStorageService,
                reactExportService,
                htmlExportService,
                pathGuard,
                tempDir.toString()
        );

        revisionManager = new RevisionManager(workspaceService, patchEngine, objectMapper);
        toolRegistry = new WorkspaceToolRegistry(workspaceService, patchEngine, sandboxedExecutionService, snapshotStorageService, objectMapper);

        // Seed initial snapshot using real Figma IR parser
        DesignNode rootNode = new FigmaParserService().parseDesignTree("""
                {"document":{"id":"0:1","name":"Landing Page","type":"CANVAS","children":[
                 {"id":"1:1","name":"Header Frame","type":"FRAME","clipsContent":true,
                  "absoluteBoundingBox":{"x":0,"y":0,"width":1440,"height":900},
                  "fills":[{"type":"SOLID","color":{"r":0.06,"g":0.09,"b":0.16}}],"children":[
                    {"id":"1:2","name":"CTA Button","type":"TEXT","characters":"Get Started",
                     "absoluteBoundingBox":{"x":100,"y":100,"width":200,"height":50}}
                  ]}
                ]}}
                """);

        FigmaSnapshot snapshot = new FigmaSnapshot(
                snapshotId, ImportSource.FIGMA_REST, "file_e2e_key", "E2E Test Document",
                "1.0", "2026-10-09T00:00:00Z", Instant.now(), "Page 1", "0:1", "Landing Page",
                "CANVAS", null, List.of(), "{}", rootNode, List.of()
        );
        snapshotStorageService.save(snapshot, null);
    }

    @Test
    void testFullReactWorkflow_Turn1_Turn2_Accept_Reject_ZIP() throws Exception {
        // --- STEP 1: Turn 1 Prompt ---
        // "Doi mau nut chinh sang vang #F59E0B, bo goc 16px, them hieu ung hover va giu nguyen layout hien tai."
        var draft1 = revisionManager.createDraftWorkspace(snapshotId, "Doi mau nut sang vang #F59E0B, bo goc 16px", "REACT");
        Path workspaceDir1 = draft1.workspaceDir();

        // AI Tool 1: List project files
        List<String> files = workspaceService.listProjectFiles(workspaceDir1);
        assertTrue(files.contains("src/App.jsx"));
        assertTrue(files.contains("src/styles/layoutly.css"));

        // AI Tool 2: Read CSS file
        String oldCss = workspaceService.readProjectFile(workspaceDir1, "src/styles/layoutly.css");

        // AI Tool 3: Apply Code Patch
        String patchCss = """
            .btn-primary {
              background-color: #F59E0B;
              border-radius: 16px;
              transition: all 0.2s ease-in-out;
            }
            .btn-primary:hover {
              background-color: #D97706;
              transform: translateY(-2px);
            }
            """;
        String updatedCss = oldCss + "\n" + patchCss;
        workspaceService.writeProjectFile(workspaceDir1, "src/styles/layoutly.css", updatedCss);

        // AI Tool 4: Run Build Validation
        var buildRes1 = sandboxedExecutionService.validateAndBuild(workspaceDir1, "REACT");
        assertTrue(buildRes1.success(), "Build validation should pass for valid CSS/JSX");

        // Compute Diff
        List<CodeDiff> diffs1 = revisionManager.computeRevisionDiffs(snapshotId, draft1.revisionId(), "REACT");
        assertFalse(diffs1.isEmpty(), "Diff should contain modified files");
        assertTrue(diffs1.stream().anyMatch(d -> d.filePath().equals("src/styles/layoutly.css")));

        // Accept Revision 1
        RevisionInfo accepted1 = revisionManager.acceptRevision(snapshotId, draft1.revisionId(), "REACT");
        assertEquals("ACCEPTED", accepted1.status());

        // Verify accepted workspace contains yellow button edits
        Path acceptedWorkspace = workspaceService.getAcceptedWorkspaceDir(snapshotId, "REACT");
        String acceptedCssContent = workspaceService.readProjectFile(acceptedWorkspace, "src/styles/layoutly.css");
        assertTrue(acceptedCssContent.contains("#F59E0B"));
        assertTrue(acceptedCssContent.contains("border-radius: 16px"));

        // --- STEP 2: Turn 2 Prompt (Multi-turn lineage) ---
        // "Chinh nut vua sua rong hon tren mobile va them khoang cach 12px voi phan tu phia tren."
        var draft2 = revisionManager.createDraftWorkspace(snapshotId, "Chinh nut rong hon tren mobile, margin-top 12px", "REACT");
        Path workspaceDir2 = draft2.workspaceDir();

        // Verify draft2 was built on top of accepted1 (preserves Turn 1 changes)
        String draft2Css = workspaceService.readProjectFile(workspaceDir2, "src/styles/layoutly.css");
        assertTrue(draft2Css.contains("#F59E0B"), "Turn 2 workspace must inherit Turn 1 accepted changes");

        // Apply Turn 2 patch
        String mobilePatch = """
            @media (max-width: 640px) {
              .btn-primary {
                width: 100%;
                margin-top: 12px;
              }
            }
            """;
        workspaceService.writeProjectFile(workspaceDir2, "src/styles/layoutly.css", draft2Css + "\n" + mobilePatch);

        // Reject Turn 2 to verify Reject isolation
        RevisionInfo rejected2 = revisionManager.rejectRevision(snapshotId, draft2.revisionId());
        assertEquals("REJECTED", rejected2.status());

        // Verify accepted workspace still holds Turn 1 changes only
        String currentAcceptedCss = workspaceService.readProjectFile(acceptedWorkspace, "src/styles/layoutly.css");
        assertTrue(currentAcceptedCss.contains("#F59E0B"));
        assertFalse(currentAcceptedCss.contains("max-width: 640px"), "Rejected revision 2 should NOT be in accepted workspace");

        // --- STEP 3: Verify Exported ZIP ---
        Map<String, byte[]> zipMap = workspaceService.exportWorkspaceAsZipMap(acceptedWorkspace);
        assertTrue(zipMap.containsKey("src/styles/layoutly.css"));
        String zipCss = new String(zipMap.get("src/styles/layoutly.css"), StandardCharsets.UTF_8);
        assertTrue(zipCss.contains("#F59E0B"));
        assertTrue(zipCss.contains("16px"));
    }

    @Test
    void testFullHtmlWorkflow() throws Exception {
        String snapshotIdHtml = "snap_e2e_html_88888";
        DesignNode rootNode = new FigmaParserService().parseDesignTree("""
                {"document":{"id":"0:1","name":"Landing Page","type":"CANVAS","children":[
                 {"id":"1:1","name":"Header Frame","type":"FRAME","clipsContent":true,
                  "absoluteBoundingBox":{"x":0,"y":0,"width":1440,"height":900},
                  "fills":[{"type":"SOLID","color":{"r":0.06,"g":0.09,"b":0.16}}],"children":[
                    {"id":"1:2","name":"CTA Button","type":"TEXT","characters":"Get Started",
                     "absoluteBoundingBox":{"x":100,"y":100,"width":200,"height":50}}
                  ]}
                ]}}
                """);

        FigmaSnapshot snapshot = new FigmaSnapshot(
                snapshotIdHtml, ImportSource.FIGMA_REST, "file_e2e_html", "HTML Test Document",
                "1.0", "2026-10-09T00:00:00Z", Instant.now(), "Page 1", "0:1", "Landing Page",
                "CANVAS", null, List.of(), "{}", rootNode, List.of()
        );
        snapshotStorageService.save(snapshot, null);

        var draft = revisionManager.createDraftWorkspace(snapshotIdHtml, "Doi mau va font cho trang HTML", "HTML");
        Path workspaceDir = draft.workspaceDir();

        List<String> files = workspaceService.listProjectFiles(workspaceDir);
        assertTrue(files.stream().anyMatch(f -> f.endsWith(".html")), "Workspace should contain HTML files: " + files);
        assertTrue(files.stream().anyMatch(f -> f.endsWith(".css")), "Workspace should contain CSS files: " + files);

        String oldCss = workspaceService.readProjectFile(workspaceDir, "styles.css");
        workspaceService.writeProjectFile(workspaceDir, "styles.css", oldCss + "\n.header { color: #F59E0B; }");

        var buildRes = sandboxedExecutionService.validateAndBuild(workspaceDir, "HTML");
        assertTrue(buildRes.success());

        revisionManager.acceptRevision(snapshotIdHtml, draft.revisionId(), "HTML");
        Path acceptedDir = workspaceService.getAcceptedWorkspaceDir(snapshotIdHtml, "HTML");
        String css = workspaceService.readProjectFile(acceptedDir, "styles.css");
        assertTrue(css.contains("#F59E0B"));
    }

    @Test
    void testSandboxIsolation() {
        Path workspaceDir = workspaceService.getAcceptedWorkspaceDir(snapshotId, "REACT");
        var result = sandboxedExecutionService.validateAndBuild(workspaceDir, "REACT");
        assertNotNull(result);
        assertTrue(result.errors().isEmpty());
    }
}
