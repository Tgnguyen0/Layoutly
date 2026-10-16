package com.tgnguyen.layoutlybe.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgnguyen.layoutlybe.model.ai.RevisionInfo;
import com.tgnguyen.layoutlybe.model.ir.DesignNode;
import com.tgnguyen.layoutlybe.model.snapshot.FigmaSnapshot;
import com.tgnguyen.layoutlybe.model.snapshot.ImportSource;
import com.tgnguyen.layoutlybe.service.AssetExportService;
import com.tgnguyen.layoutlybe.service.CssGeneratorService;
import com.tgnguyen.layoutlybe.service.HtmlGeneratorService;
import com.tgnguyen.layoutlybe.service.HtmlProjectExportService;
import com.tgnguyen.layoutlybe.service.ReactGeneratorService;
import com.tgnguyen.layoutlybe.service.ReactProjectExportService;
import com.tgnguyen.layoutlybe.service.SnapshotStorageService;
import com.tgnguyen.layoutlybe.service.SnapshotStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RevisionManagerTest {

    private ProjectWorkspaceService workspaceService;
    private RevisionManager revisionManager;
    private SnapshotStorageService snapshotStorageService;
    private String snapshotId = "snap_test_12345678";

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        snapshotStorageService = new SnapshotStorageService(objectMapper, tempDir);
        WorkspacePathGuard pathGuard = new WorkspacePathGuard();
        CodePatchEngine patchEngine = new CodePatchEngine();

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

        // Seed snapshot
        DesignNode rootNode = DesignNode.builder().id("1:1").name("Test Page").type("FRAME").children(List.of()).build();
        FigmaSnapshot snapshot = new FigmaSnapshot(
                snapshotId, ImportSource.FIGMA_REST, "file123", "Test Document",
                "1.0", "2026-10-09T00:00:00Z", Instant.now(), "Page 1", "1:1", "Test Page",
                "FRAME", null, List.of(), "{}", rootNode, List.of()
        );
        snapshotStorageService.save(snapshot, null);
    }

    @Test
    void testCreateDraftAndAcceptRevision() {
        var draft = revisionManager.createDraftWorkspace(snapshotId, "Doi mau nut thanh vang", "REACT");
        assertNotNull(draft.revisionId());
        assertEquals("DRAFT", draft.info().status());

        // Modify a file in draft workspace
        workspaceService.writeProjectFile(draft.workspaceDir(), "src/App.jsx", "// Edited by AI\nexport default function App() { return <h1>Modified</h1>; }");

        // Accept revision
        RevisionInfo accepted = revisionManager.acceptRevision(snapshotId, draft.revisionId(), "REACT");
        assertEquals("ACCEPTED", accepted.status());

        // Verify accepted workspace updated
        Path acceptedDir = workspaceService.getAcceptedWorkspaceDir(snapshotId, "REACT");
        String acceptedContent = workspaceService.readProjectFile(acceptedDir, "src/App.jsx");
        assertTrue(acceptedContent.contains("Edited by AI"));
    }

    @Test
    void testRejectRevision() {
        var draft = revisionManager.createDraftWorkspace(snapshotId, "Them navbar", "REACT");
        RevisionInfo rejected = revisionManager.rejectRevision(snapshotId, draft.revisionId());
        assertEquals("REJECTED", rejected.status());
    }

    @Test
    void persistsMostRecentlyAcceptedRevisionEvenWhenItWasCreatedEarlier() {
        var first = revisionManager.createDraftWorkspace(snapshotId, "First draft", "REACT");
        workspaceService.writeProjectFile(first.workspaceDir(), "src/App.jsx", "export default function App() { return 'first'; }");
        var second = revisionManager.createDraftWorkspace(snapshotId, "Second draft", "REACT");
        workspaceService.writeProjectFile(second.workspaceDir(), "src/App.jsx", "export default function App() { return 'second'; }");
        revisionManager.acceptRevision(snapshotId, second.revisionId(), "REACT");
        revisionManager.acceptRevision(snapshotId, first.revisionId(), "REACT");
        RevisionManager restored = new RevisionManager(workspaceService, new CodePatchEngine(), new ObjectMapper().findAndRegisterModules());
        assertEquals(first.revisionId(), restored.getAcceptedRevision(snapshotId).revisionId());
        assertTrue(workspaceService.readProjectFile(workspaceService.getAcceptedWorkspaceDir(snapshotId, "REACT"), "src/App.jsx").contains("'first'"));
        assertEquals(2, restored.getRevisionHistory(snapshotId).stream().filter(item -> "ACCEPTED".equals(item.status())).count());
    }
}
