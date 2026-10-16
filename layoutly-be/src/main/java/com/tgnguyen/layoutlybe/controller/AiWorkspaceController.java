package com.tgnguyen.layoutlybe.controller;

import com.tgnguyen.layoutlybe.model.ai.AiChatRequest;
import com.tgnguyen.layoutlybe.model.ai.AiChatResponse;
import com.tgnguyen.layoutlybe.model.ai.CodeDiff;
import com.tgnguyen.layoutlybe.model.ai.RevisionInfo;
import com.tgnguyen.layoutlybe.service.ai.AiAgentLoopService;
import com.tgnguyen.layoutlybe.service.ai.ProjectWorkspaceService;
import com.tgnguyen.layoutlybe.service.ai.RevisionManager;
import com.tgnguyen.layoutlybe.service.ai.AiAgentJobService;
import com.tgnguyen.layoutlybe.service.ai.OllamaAiProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/snapshots/{snapshotId}/ai")
public class AiWorkspaceController {
    @Autowired(required = false)
    private com.tgnguyen.layoutlybe.service.AiConversationService conversations;
    @GetMapping("/conversation")
    public Map<String,?> conversation(@PathVariable String snapshotId) { return Map.of("messages", conversations.history(snapshotId)); }
    @Autowired(required = false)
    private com.tgnguyen.layoutlybe.service.SavedProjectService savedProjects;

    private final AiAgentLoopService agentLoopService;
    private final RevisionManager revisionManager;
    private final ProjectWorkspaceService workspaceService;
    private AiAgentJobService jobs;
    private com.tgnguyen.layoutlybe.service.ai.PreviewScreenshotService screenshots;

    @Autowired(required = false)
    public void setScreenshots(com.tgnguyen.layoutlybe.service.ai.PreviewScreenshotService screenshots) { this.screenshots = screenshots; }

    @Autowired
    public void setJobs(AiAgentJobService jobs) { this.jobs = jobs; }

    @Autowired
    public AiWorkspaceController(
            AiAgentLoopService agentLoopService,
            RevisionManager revisionManager,
            ProjectWorkspaceService workspaceService) {
        this.agentLoopService = agentLoopService;
        this.revisionManager = revisionManager;
        this.workspaceService = workspaceService;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAiStatus(@PathVariable String snapshotId) {
        Map<String, Object> summary = agentLoopService.getProviderRegistry().getStatusSummary();
        summary.put("snapshotId", snapshotId);
        boolean configured = (boolean) summary.get("available");
        summary.putIfAbsent("message", configured
                ? "AI Coding Agent đã sẵn sàng (" + summary.get("activeProvider") + " - " + summary.get("activeModel") + ")."
                : "Chưa cấu hình AI Key (OPENROUTER_API_KEY, GEMINI_API_KEY, hoặc OPENAI_API_KEY).");
        return ResponseEntity.ok(summary);
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AiChatResponse> processChat(
            @PathVariable String snapshotId,
            @RequestBody AiChatRequest request) {

        AiChatResponse response = agentLoopService.processUserPrompt(snapshotId, request, null);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/revisions")
    public ResponseEntity<List<RevisionInfo>> getRevisions(@PathVariable String snapshotId) {
        return ResponseEntity.ok(revisionManager.getRevisionHistory(snapshotId));
    }

    @PostMapping("/jobs")
    public Map<String, Object> startJob(@PathVariable String snapshotId, @RequestBody AiChatRequest request) {
        return jobs.start(snapshotId, request);
    }

    @GetMapping("/jobs/{jobId}")
    public Map<String, Object> getJob(@PathVariable String snapshotId, @PathVariable String jobId) { return jobs.get(snapshotId, jobId); }

    @PostMapping("/jobs/{jobId}/cancel")
    public Map<String, Object> cancelJob(@PathVariable String snapshotId, @PathVariable String jobId) { return jobs.cancel(snapshotId, jobId); }

    @GetMapping(value = "/screenshots/{captureId}", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] getScreenshot(@PathVariable String snapshotId, @PathVariable String captureId) throws java.io.IOException { return screenshots.read(snapshotId, captureId); }

    @PostMapping("/models/load")
    public Map<String, Object> loadModel(@PathVariable String snapshotId, @RequestBody Map<String, String> request) {
        var provider = agentLoopService.getProviderRegistry().getProvider("ollama");
        if (!(provider instanceof OllamaAiProvider local)) throw new IllegalStateException("Local Ollama is not configured.");
        return local.loadModel(request.getOrDefault("model", local.getModelName()));
    }

    @GetMapping("/accepted")
    public ResponseEntity<RevisionInfo> getAcceptedRevision(@PathVariable String snapshotId) {
        return ResponseEntity.ok(revisionManager.getAcceptedRevision(snapshotId));
    }

    // Read-only source access for the editor. Only known revisions can be opened.
    private Path resolveWorkspace(String snapshotId, String revisionId, String framework) {
        if (revisionId == null || revisionId.isBlank()) {
            return workspaceService.getAcceptedWorkspaceDir(snapshotId, framework);
        }
        if (!revisionId.equals("rev_000_baseline") && revisionManager.getRevisionHistory(snapshotId).stream()
                .noneMatch(item -> item.revisionId().equals(revisionId))) {
            throw new IllegalArgumentException("Unknown revision ID");
        }
        return workspaceService.getOrInitializeWorkspace(snapshotId, revisionId, framework);
    }

    @GetMapping("/files")
    public ResponseEntity<List<String>> getFiles(@PathVariable String snapshotId,
            @RequestParam(required = false) String revisionId,
            @RequestParam(defaultValue = "REACT") String framework) {
        return ResponseEntity.ok(workspaceService.listProjectFiles(resolveWorkspace(snapshotId, revisionId, framework)));
    }

    @GetMapping("/file")
    public ResponseEntity<byte[]> getFile(@PathVariable String snapshotId,
            @RequestParam String path, @RequestParam(required = false) String revisionId,
            @RequestParam(defaultValue = "REACT") String framework) throws java.io.IOException {
        Path root = resolveWorkspace(snapshotId, revisionId, framework);
        if (!workspaceService.listProjectFiles(root).contains(path)) {
            return ResponseEntity.notFound().build();
        }
        // The file list is filtered by WorkspacePathGuard; validate again before reading.
        byte[] bytes = workspaceService.readProjectFileBytes(root, path);
        MediaType media = org.springframework.http.MediaTypeFactory.getMediaType(path)
                .filter(type -> type.getType().equals("image") || type.getType().equals("font"))
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(media)
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox").body(bytes);
    }

    @GetMapping("/revisions/{revisionId}/diff")
    public ResponseEntity<List<CodeDiff>> getRevisionDiffs(
            @PathVariable String snapshotId,
            @PathVariable String revisionId,
            @RequestParam(defaultValue = "REACT") String framework) {
        return ResponseEntity.ok(revisionManager.computeRevisionDiffs(snapshotId, revisionId, framework));
    }

    @PostMapping("/revisions/{revisionId}/accept")
    public ResponseEntity<RevisionInfo> acceptRevision(
            @PathVariable String snapshotId,
            @PathVariable String revisionId,
            @RequestParam(defaultValue = "REACT") String framework) {
        RevisionInfo updated = revisionManager.acceptRevision(snapshotId, revisionId, framework);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/revisions/{revisionId}/reject")
    public ResponseEntity<RevisionInfo> rejectRevision(
            @PathVariable String snapshotId,
            @PathVariable String revisionId) {
        RevisionInfo updated = revisionManager.rejectRevision(snapshotId, revisionId);
        return ResponseEntity.ok(updated);
    }

    @GetMapping(value = "/preview", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getRevisionPreview(
            @PathVariable String snapshotId,
            @RequestParam(required = false) String revisionId,
            @RequestParam(defaultValue = "REACT") String framework) {

        Path workspaceDir;
        if (revisionId != null && !revisionId.isBlank()) {
            workspaceDir = workspaceService.getOrInitializeWorkspace(snapshotId, revisionId, framework);
        } else {
            workspaceDir = workspaceService.getAcceptedWorkspaceDir(snapshotId, framework);
        }

        List<String> files = workspaceService.listProjectFiles(workspaceDir);

        if ("HTML".equalsIgnoreCase(framework)) {
            String htmlFile = files.stream().filter(f -> f.endsWith(".html")).findFirst().orElse("index.html");
            String htmlContent = workspaceService.readProjectFile(workspaceDir, htmlFile);
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(htmlContent);
        } else {
            // React Preview fallback / single bundle runner
            String indexHtml = files.contains("index.html")
                    ? workspaceService.readProjectFile(workspaceDir, "index.html")
                    : "<html><body><div id='root'></div></body></html>";
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(indexHtml);
        }
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportRevisionZip(
            @PathVariable String snapshotId,
            @RequestParam(required = false) String revisionId,
            @RequestParam(defaultValue = "REACT") String framework) {

        Path workspaceDir = resolveWorkspace(snapshotId, revisionId, framework);

        Map<String, byte[]> fileMap = workspaceService.exportWorkspaceAsZipMap(workspaceDir);

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                for (Map.Entry<String, byte[]> entry : fileMap.entrySet()) {
                    zos.putNextEntry(new ZipEntry(entry.getKey()));
                    zos.write(entry.getValue());
                    zos.closeEntry();
                }
            }

            String filename = "layoutly-ai-export-" + snapshotId + ".zip";
            if (savedProjects != null) savedProjects.recordExport(com.tgnguyen.layoutlybe.security.CurrentUser.id(), snapshotId, framework, revisionId, baos.size());
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                    .body(baos.toByteArray());

        } catch (Exception e) {
            throw new RuntimeException("Loi export revision ZIP: " + e.getMessage(), e);
        }
    }
}
