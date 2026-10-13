package com.tgnguyen.layoutlybe.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgnguyen.layoutlybe.model.ai.AiChatRequest;
import com.tgnguyen.layoutlybe.model.ai.AiChatResponse;
import com.tgnguyen.layoutlybe.model.ir.DesignNode;
import com.tgnguyen.layoutlybe.model.snapshot.FigmaSnapshot;
import com.tgnguyen.layoutlybe.model.snapshot.ImportSource;
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

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpenRouterLiveApiTest {

    private ProjectWorkspaceService workspaceService;
    private RevisionManager revisionManager;
    private WorkspaceToolRegistry toolRegistry;
    private SandboxedExecutionService sandboxedExecutionService;
    private SnapshotStorageService snapshotStorageService;
    private CodePatchEngine patchEngine;
    private AiAgentLoopService agentLoopService;
    private String snapshotId = "snap_live_openrouter_999";

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

        // Seed snapshot
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
                snapshotId, ImportSource.FIGMA_REST, "file_live_key", "Live Test Document",
                "1.0", "2026-10-09T00:00:00Z", Instant.now(), "Page 1", "0:1", "Landing Page",
                "CANVAS", null, List.of(), "{}", rootNode, List.of()
        );
        snapshotStorageService.save(snapshot, null);

        String openRouterApiKey = System.getenv("OPENROUTER_API_KEY");
        if (openRouterApiKey == null || openRouterApiKey.isBlank()) {
            openRouterApiKey = System.getProperty("OPENROUTER_API_KEY", "");
        }

        OpenRouterProvider openRouterProvider = new OpenRouterProvider(
                objectMapper,
                openRouterApiKey,
                "openrouter/free",
                "https://openrouter.ai/api/v1"
        );
        OpenAiProvider openAiProvider = new OpenAiProvider(objectMapper, "", "gpt-4o", "https://api.openai.com/v1");
        GeminiProvider geminiProvider = new GeminiProvider(objectMapper, "", "gemini-2.0-flash", "https://generativelanguage.googleapis.com/v1beta/openai");

        AiProviderRegistry registry = new AiProviderRegistry(
                List.of(openRouterProvider, geminiProvider, openAiProvider),
                "openrouter"
        );

        agentLoopService = new AiAgentLoopService(
                registry,
                toolRegistry,
                revisionManager,
                workspaceService,
                sandboxedExecutionService,
                objectMapper
        );
    }

    @Test
    void testLiveOpenRouterWorkflow() {
        String apiKey = System.getenv("OPENROUTER_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getProperty("OPENROUTER_API_KEY");
        }

        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("[SKIP] Skipped live OpenRouter test because OPENROUTER_API_KEY is not set.");
            return;
        }

        System.out.println("[LIVE TEST] Running real OpenRouter API call with model openrouter/free...");

        AiChatRequest request = new AiChatRequest(
                "Đổi màu nút chính sang vàng #F59E0B, bo góc 16px, thêm hiệu ứng hover và giữ nguyên layout hiện tại.",
                "REACT",
                "1:2",
                "CTA Button",
                List.of(),
                "openrouter",
                "openrouter/free"
        );

        AiChatResponse response = agentLoopService.processUserPrompt(snapshotId, request, null);

        assertNotNull(response);
        assertNotNull(response.revisionId());
        assertEquals("DRAFT", response.status());
        System.out.println("-> Revision created: " + response.revisionId());
        System.out.println("-> Modified files: " + response.modifiedFiles());
        System.out.println("-> AI Assistant Message: " + response.message());

        // Accept revision
        var acceptedRev = revisionManager.acceptRevision(snapshotId, response.revisionId(), "REACT");
        assertEquals("ACCEPTED", acceptedRev.status());
        System.out.println("-> Revision accepted successfully!");

        // Export ZIP
        Path acceptedDir = workspaceService.getAcceptedWorkspaceDir(snapshotId, "REACT");
        var zipMap = workspaceService.exportWorkspaceAsZipMap(acceptedDir);
        assertFalse(zipMap.isEmpty());
        assertTrue(zipMap.containsKey("src/App.jsx"));
        System.out.println("-> ZIP Export contains " + zipMap.size() + " files.");
    }
}
