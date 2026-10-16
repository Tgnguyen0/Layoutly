package com.tgnguyen.layoutlybe.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Runs only our fixed browser driver, never an AI-created package script. */
@Service
public class PreviewScreenshotService {
    @org.springframework.beans.factory.annotation.Autowired
    private com.tgnguyen.layoutlybe.service.AccountService accounts;
    @org.springframework.beans.factory.annotation.Autowired
    private com.tgnguyen.layoutlybe.persistence.SnapshotRepository snapshots;
    private final Path script;
    private final Path directory;
    private final ObjectMapper mapper;
    public PreviewScreenshotService(ObjectMapper mapper,
            @Value("${layoutly.ai.preview.capture-script:../layoutly-fe/scripts/capture-ai-preview.mjs}") String script,
            @Value("${layoutly.ai.preview.capture-directory:../.local-ai/screenshots}") String directory) {
        this.mapper = mapper;
        this.script = Path.of(script).toAbsolutePath().normalize();
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }
    public Map<String, Object> capture(String snapshotId, String revisionId, String framework) throws Exception {
        if (!snapshotId.matches("[a-zA-Z0-9_-]{8,80}") || !revisionId.matches("[a-zA-Z0-9_-]{1,80}") || !List.of("REACT", "HTML").contains(framework)) throw new IllegalArgumentException("Invalid capture target.");
        if (!Files.isRegularFile(script)) return Map.of("status", "UNAVAILABLE", "message", "Trusted local screenshot driver is not installed.");
        Path scoped = directory.resolve(snapshotId);
        Files.createDirectories(scoped);
        String captureId = UUID.randomUUID().toString();
        Path output = scoped.resolve(captureId + ".png");
        ProcessBuilder builder = new ProcessBuilder("node", script.toString(), snapshotId, revisionId, framework, output.toString());
        builder.directory(script.getParent().getParent().toFile());
        builder.environment().keySet().removeIf(name -> name.toUpperCase().matches(".*(TOKEN|SECRET|API_KEY|PASSWORD).*"));
        var login = accounts.captureSession(snapshots.findById(snapshotId).orElseThrow().ownerId);
        builder.environment().put("LAYOUTLY_CAPTURE_SESSION", login.token());
        Process process = builder.start();
        try {
            if (!process.waitFor(35, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IllegalStateException("Local screenshot timed out."); }
            String stdout = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (process.exitValue() != 0 || !Files.isRegularFile(output)) throw new IllegalStateException("Local screenshot failed: " + stdout.substring(0, Math.min(stdout.length(), 700)));
            return Map.of("status", "CAPTURED", "captureId", captureId, "url", "/api/snapshots/" + snapshotId + "/ai/screenshots/" + captureId,
                    "bytes", Files.size(output), "sourceRevisionId", revisionId, "externalUploads", false,
                    "note", "Real browser screenshot. This text-only model receives image metadata, not image pixels.");
        } catch (InterruptedException e) { process.destroyForcibly(); Thread.currentThread().interrupt(); throw new java.util.concurrent.CancellationException("Screenshot cancelled."); }
        finally { accounts.logout(login.token()); }
    }
    public byte[] read(String snapshotId, String captureId) throws java.io.IOException {
        if (!snapshotId.matches("[a-zA-Z0-9_-]{8,80}")) throw new IllegalArgumentException("Invalid snapshot ID.");
        if (!captureId.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException("Invalid capture ID.");
        return Files.readAllBytes(directory.resolve(snapshotId).resolve(captureId + ".png"));
    }
}
