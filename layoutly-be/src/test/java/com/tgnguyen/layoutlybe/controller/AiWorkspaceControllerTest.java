package com.tgnguyen.layoutlybe.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgnguyen.layoutlybe.model.ai.AiChatResponse;
import com.tgnguyen.layoutlybe.model.ai.AiUsage;
import com.tgnguyen.layoutlybe.model.ai.RevisionInfo;
import com.tgnguyen.layoutlybe.service.ai.AiAgentLoopService;
import com.tgnguyen.layoutlybe.service.ai.ProjectWorkspaceService;
import com.tgnguyen.layoutlybe.service.ai.RevisionManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiWorkspaceController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
class AiWorkspaceControllerTest {
    @MockBean private com.tgnguyen.layoutlybe.config.SnapshotAuthorizationConfig authorization;
    @MockBean private com.tgnguyen.layoutlybe.service.SavedProjectService savedProjects;
    @MockBean private com.tgnguyen.layoutlybe.service.AiConversationService conversations;
    @MockBean private com.tgnguyen.layoutlybe.service.AccountService accounts;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiAgentLoopService agentLoopService;

    @MockBean
    private RevisionManager revisionManager;

    @MockBean
    private ProjectWorkspaceService workspaceService;

    @MockBean
    private com.tgnguyen.layoutlybe.service.ai.AiAgentJobService jobs;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testGetStatus() throws Exception {
        com.tgnguyen.layoutlybe.service.ai.AiProviderRegistry registry = org.mockito.Mockito.mock(com.tgnguyen.layoutlybe.service.ai.AiProviderRegistry.class);
        when(agentLoopService.getProviderRegistry()).thenReturn(registry);
        when(registry.getStatusSummary()).thenReturn(new java.util.HashMap<>(java.util.Map.of(
                "available", false,
                "activeProvider", "openrouter",
                "activeModel", "google/gemini-2.0-flash-lite-preview-02-05:free",
                "providers", java.util.List.of()
        )));

        mockMvc.perform(get("/api/snapshots/snap_12345678/ai/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotId").value("snap_12345678"))
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void listsFilesAndReadsExactRevisionBytes() throws Exception {
        java.nio.file.Path root = java.nio.file.Path.of("test-workspace");
        RevisionInfo revision = new RevisionInfo("rev_001", null, "snap_12345678", "DRAFT", "REACT",
                "test", Instant.now(), List.of("src/App.jsx"), true, "", AiUsage.empty());
        when(revisionManager.getRevisionHistory("snap_12345678")).thenReturn(List.of(revision));
        when(workspaceService.getOrInitializeWorkspace("snap_12345678", "rev_001", "REACT")).thenReturn(root);
        when(workspaceService.listProjectFiles(root)).thenReturn(List.of("src/App.jsx"));
        when(workspaceService.readProjectFileBytes(root, "src/App.jsx")).thenReturn("revision source".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mockMvc.perform(get("/api/snapshots/snap_12345678/ai/files").param("revisionId", "rev_001"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0]").value("src/App.jsx"));
        mockMvc.perform(get("/api/snapshots/snap_12345678/ai/file").param("revisionId", "rev_001").param("path", "src/App.jsx"))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("revision source"));
        mockMvc.perform(get("/api/snapshots/snap_12345678/ai/file").param("revisionId", "rev_001").param("path", "../outside"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnknownRevisionSourceAccess() throws Exception {
        when(revisionManager.getRevisionHistory("snap_12345678")).thenReturn(List.of());
        mockMvc.perform(get("/api/snapshots/snap_12345678/ai/files").param("revisionId", "../../outside"))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verify(workspaceService, org.mockito.Mockito.never()).getOrInitializeWorkspace(any(), any(), any());
    }

    @Test
    void testChatEndpoint() throws Exception {
        when(agentLoopService.processUserPrompt(eq("snap_12345678"), any(), any()))
                .thenReturn(new AiChatResponse(
                        "Da doi mau nut thanh vang",
                        "rev_001",
                        "DRAFT",
                        List.of("src/App.jsx"),
                        List.of(),
                        true,
                        "",
                        new AiUsage(100, 50, 150, 0.001)
                ));

        String requestJson = """
            {
              "prompt": "Doi mau nut thanh vang",
              "targetFramework": "REACT"
            }
            """;

        mockMvc.perform(post("/api/snapshots/snap_12345678/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisionId").value("rev_001"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.modifiedFiles[0]").value("src/App.jsx"));
    }

    @Test
    void testAcceptRevisionEndpoint() throws Exception {
        when(revisionManager.acceptRevision(eq("snap_12345678"), eq("rev_001"), eq("REACT")))
                .thenReturn(new RevisionInfo(
                        "rev_001", "rev_000_baseline", "snap_12345678",
                        "ACCEPTED", "REACT", "Doi mau nut", Instant.now(),
                        List.of("src/App.jsx"), true, "", AiUsage.empty()
                ));

        mockMvc.perform(post("/api/snapshots/snap_12345678/ai/revisions/rev_001/accept?framework=REACT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisionId").value("rev_001"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }
}
