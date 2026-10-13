package com.tgnguyen.layoutlybe.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgnguyen.layoutlybe.model.ai.AiUsage;
import com.tgnguyen.layoutlybe.model.ai.CodeDiff;
import com.tgnguyen.layoutlybe.model.ai.RevisionInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class RevisionManager {

    private final ProjectWorkspaceService workspaceService;
    private final CodePatchEngine patchEngine;
    private final ObjectMapper objectMapper;

    @Autowired
    public RevisionManager(
            ProjectWorkspaceService workspaceService,
            CodePatchEngine patchEngine,
            ObjectMapper objectMapper) {
        this.workspaceService = workspaceService;
        this.patchEngine = patchEngine;
        this.objectMapper = objectMapper.copy();
    }

    public record DraftWorkspace(
            String revisionId,
            Path workspaceDir,
            RevisionInfo info
    ) {}

    public synchronized DraftWorkspace createDraftWorkspace(String snapshotId, String prompt, String targetFramework) {
        List<RevisionInfo> history = getRevisionHistory(snapshotId);
        int nextSeq = history.size() + 1;
        String revisionId = String.format("rev_%03d_%d", nextSeq, System.currentTimeMillis() % 10000);

        String parentRevisionId = history.isEmpty() ? "rev_000_baseline" : history.get(history.size() - 1).revisionId();

        Path workspaceDir = workspaceService.getOrInitializeWorkspace(snapshotId, revisionId, targetFramework);

        RevisionInfo info = new RevisionInfo(
                revisionId,
                parentRevisionId,
                snapshotId,
                "DRAFT",
                targetFramework == null ? "REACT" : targetFramework.toUpperCase(),
                prompt,
                Instant.now(),
                new ArrayList<>(),
                true,
                "",
                AiUsage.empty()
        );

        saveRevisionRecord(snapshotId, info);
        return new DraftWorkspace(revisionId, workspaceDir, info);
    }

    public synchronized void updateDraftStatus(String snapshotId, String revisionId, List<String> modifiedFiles, boolean buildSuccess, String diagnostics, AiUsage usage) {
        List<RevisionInfo> history = getRevisionHistory(snapshotId);
        for (int i = 0; i < history.size(); i++) {
            RevisionInfo item = history.get(i);
            if (item.revisionId().equals(revisionId)) {
                RevisionInfo updated = new RevisionInfo(
                        item.revisionId(),
                        item.parentRevisionId(),
                        item.snapshotId(),
                        item.status(),
                        item.targetFramework(),
                        item.prompt(),
                        item.createdAt(),
                        modifiedFiles,
                        buildSuccess,
                        diagnostics,
                        usage
                );
                history.set(i, updated);
                saveAllHistory(snapshotId, history);
                break;
            }
        }
    }

    public synchronized RevisionInfo acceptRevision(String snapshotId, String revisionId, String targetFramework) {
        List<RevisionInfo> history = getRevisionHistory(snapshotId);
        RevisionInfo target = null;
        for (int i = 0; i < history.size(); i++) {
            RevisionInfo item = history.get(i);
            if (item.revisionId().equals(revisionId)) {
                target = new RevisionInfo(
                        item.revisionId(), item.parentRevisionId(), item.snapshotId(),
                        "ACCEPTED", item.targetFramework(), item.prompt(), item.createdAt(),
                        item.modifiedFiles(), item.buildSuccess(), item.buildDiagnostics(), item.usage()
                );
                history.set(i, target);
                break;
            }
        }

        if (target == null) {
            throw new IllegalArgumentException("Khong tim thay revision ID: " + revisionId);
        }

        Path revisionDir = workspaceService.getOrInitializeWorkspace(snapshotId, revisionId, targetFramework);
        workspaceService.updateAcceptedWorkspace(snapshotId, revisionDir);

        saveAllHistory(snapshotId, history);
        return target;
    }

    public synchronized RevisionInfo rejectRevision(String snapshotId, String revisionId) {
        List<RevisionInfo> history = getRevisionHistory(snapshotId);
        RevisionInfo target = null;
        for (int i = 0; i < history.size(); i++) {
            RevisionInfo item = history.get(i);
            if (item.revisionId().equals(revisionId)) {
                target = new RevisionInfo(
                        item.revisionId(), item.parentRevisionId(), item.snapshotId(),
                        "REJECTED", item.targetFramework(), item.prompt(), item.createdAt(),
                        item.modifiedFiles(), item.buildSuccess(), item.buildDiagnostics(), item.usage()
                );
                history.set(i, target);
                break;
            }
        }

        if (target == null) {
            throw new IllegalArgumentException("Khong tim thay revision ID: " + revisionId);
        }

        saveAllHistory(snapshotId, history);
        return target;
    }

    public List<CodeDiff> computeRevisionDiffs(String snapshotId, String revisionId, String targetFramework) {
        Path draftDir = workspaceService.getOrInitializeWorkspace(snapshotId, revisionId, targetFramework);
        Path acceptedDir = workspaceService.getAcceptedWorkspaceDir(snapshotId, targetFramework);

        List<String> draftFiles = workspaceService.listProjectFiles(draftDir);
        List<String> acceptedFiles = workspaceService.listProjectFiles(acceptedDir);

        List<CodeDiff> diffs = new ArrayList<>();

        for (String relative : draftFiles) {
            String draftContent = workspaceService.readProjectFile(draftDir, relative);
            String acceptedContent = null;
            if (acceptedFiles.contains(relative)) {
                acceptedContent = workspaceService.readProjectFile(acceptedDir, relative);
            }

            if (!draftContent.equals(acceptedContent)) {
                diffs.add(patchEngine.generateDiff(relative, acceptedContent, draftContent));
            }
        }

        for (String relative : acceptedFiles) {
            if (!draftFiles.contains(relative)) {
                String acceptedContent = workspaceService.readProjectFile(acceptedDir, relative);
                diffs.add(patchEngine.generateDiff(relative, acceptedContent, null));
            }
        }

        return diffs;
    }

    public List<RevisionInfo> getRevisionHistory(String snapshotId) {
        Path historyFile = getHistoryFile(snapshotId);
        if (!Files.isRegularFile(historyFile)) return new ArrayList<>();

        try {
            return objectMapper.readValue(historyFile.toFile(), new TypeReference<List<RevisionInfo>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void saveRevisionRecord(String snapshotId, RevisionInfo info) {
        List<RevisionInfo> history = getRevisionHistory(snapshotId);
        history.add(info);
        saveAllHistory(snapshotId, history);
    }

    private void saveAllHistory(String snapshotId, List<RevisionInfo> history) {
        Path historyFile = getHistoryFile(snapshotId);
        try {
            Files.createDirectories(historyFile.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(historyFile.toFile(), history);
        } catch (Exception e) {
            throw new RuntimeException("Loi khi luu history revision: " + e.getMessage(), e);
        }
    }

    private Path getHistoryFile(String snapshotId) {
        return workspaceService.getProjectDirectory(snapshotId).resolve("revisions_history.json");
    }
}
