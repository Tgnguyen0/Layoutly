package com.tgnguyen.layoutlybe.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.tgnguyen.layoutlybe.model.ai.AiChatRequest;
import com.tgnguyen.layoutlybe.model.ai.AiChatResponse;
import com.tgnguyen.layoutlybe.model.ai.AiMessage;
import com.tgnguyen.layoutlybe.model.ai.AiToolCall;
import com.tgnguyen.layoutlybe.model.ai.AiUsage;
import com.tgnguyen.layoutlybe.model.ai.AiAgentEvent;
import com.tgnguyen.layoutlybe.model.ai.CodeDiff;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Semaphore;

@Service
public class AiAgentLoopService {
    @Autowired(required = false)
    private com.tgnguyen.layoutlybe.service.AiConversationService conversations;

    private final AiProviderRegistry providerRegistry;
    private final WorkspaceToolRegistry toolRegistry;
    private final RevisionManager revisionManager;
    private final ProjectWorkspaceService workspaceService;
    private final SandboxedExecutionService sandboxedExecutionService;
    private final ObjectMapper objectMapper;
    private final Semaphore agentSlot = new Semaphore(1);

    @Autowired
    public AiAgentLoopService(
            AiProviderRegistry providerRegistry,
            WorkspaceToolRegistry toolRegistry,
            RevisionManager revisionManager,
            ProjectWorkspaceService workspaceService,
            SandboxedExecutionService sandboxedExecutionService,
            ObjectMapper objectMapper) {
        this.providerRegistry = providerRegistry;
        this.toolRegistry = toolRegistry;
        this.revisionManager = revisionManager;
        this.workspaceService = workspaceService;
        this.sandboxedExecutionService = sandboxedExecutionService;
        this.objectMapper = objectMapper.copy();
    }

    public boolean isAiConfigured() {
        return providerRegistry.isAnyProviderConfigured();
    }

    public AiProviderRegistry getProviderRegistry() {
        return providerRegistry;
    }

    public AiChatResponse processUserPrompt(String snapshotId, AiChatRequest request, Consumer<String> streamConsumer) {
        return processUserPrompt(snapshotId, request, streamConsumer, event -> {});
    }

    public AiChatResponse processUserPrompt(String snapshotId, AiChatRequest request, Consumer<String> streamConsumer, Consumer<AiAgentEvent> events) {
        if (!agentSlot.tryAcquire()) throw new IllegalStateException("A local AI request is already running. Wait or cancel it first.");
        try {
            if (request == null || request.prompt() == null || request.prompt().isBlank() || request.prompt().length() > 4000)
                throw new IllegalArgumentException("Prompt must contain 1–4000 characters.");
            var conversation = conversations == null ? null : conversations.userMessage(snapshotId, request.prompt(), request.targetFramework());
            try {
                var result = runPrompt(snapshotId, request, streamConsumer, events);
                if (conversation != null) conversations.message(conversation, "assistant", result);
                return result;
            } catch (RuntimeException ex) {
                if (conversation != null) conversations.message(conversation, "assistant", Map.of("message", "Yêu cầu chưa hoàn tất.", "status", "ERROR"));
                throw ex;
            }
        }
        finally { agentSlot.release(); }
    }

    private AiChatResponse runPrompt(String snapshotId, AiChatRequest request, Consumer<String> streamConsumer, Consumer<AiAgentEvent> events) {
        if (request == null || request.prompt() == null || request.prompt().isBlank() || request.prompt().length() > 4000) throw new IllegalArgumentException("Prompt must contain 1–4000 characters.");
        events.accept(AiAgentEvent.of("ANALYZING", null, "Validating provider, model and workspace."));
        AiProvider aiProvider = providerRegistry.getProvider(request.provider());

        if (!aiProvider.isConfigured()) {
            throw new IllegalStateException("AI Provider is unavailable: " + aiProvider.getProviderDisplayName());
        }

        String targetModel = (request.model() != null && !request.model().isBlank())
                ? request.model() : aiProvider.getModelName();

        if (!aiProvider.supportsToolCalling(targetModel)) {
            throw new IllegalStateException("Model '" + targetModel + "' is not installed, unavailable or does not support native tool calling. No cloud fallback will be used.");
        }

        String targetFramework = (request.targetFramework() == null || request.targetFramework().isBlank())
                ? "REACT" : request.targetFramework().toUpperCase();

        // Step 1: Create draft workspace
        var draft = revisionManager.createDraftWorkspace(snapshotId, request.prompt(), targetFramework);
        Path workspaceDir = draft.workspaceDir();

        try {

        // Step 2: Build system prompt
        List<AiMessage> messages = new ArrayList<>();
        messages.add(AiMessage.system(buildSystemPrompt(targetFramework, request.selectedNodeId(), request.selectedNodeName())));

        // Append conversation history if provided
        if (request.history() != null) {
            request.history().stream().filter(item -> List.of("user", "assistant").contains(item.role()) && item.content() != null)
                    .skip(Math.max(0, request.history().size() - 4)).forEach(item -> messages.add(new AiMessage(item.role(), item.content().substring(0, Math.min(900, item.content().length())), null, null, null)));
        }

        // Add current user prompt
        messages.add(AiMessage.user(request.prompt()));

        List<Map<String, Object>> tools = toolRegistry.getToolDefinitions();

        int maxIterations = 8;
        int currentIteration = 0;
        int totalPromptTokens = 0;
        int totalCompletionTokens = 0;

        String finalAssistantMessage = "";
        List<String> modifiedFiles = new ArrayList<>();
        long deadline = System.nanoTime() + java.time.Duration.ofMinutes(4).toNanos();
        int executedTools = 0;

        while (currentIteration < maxIterations) {
            currentIteration++;
            checkActive(deadline);
            compactContext(messages);
            events.accept(AiAgentEvent.of("INFERENCE", null, "Local model inference, round " + currentIteration + "/" + maxIterations));

            var completion = aiProvider.complete(messages, tools, 0.2, targetModel);
            totalPromptTokens += completion.promptTokens();
            totalCompletionTokens += completion.completionTokens();

            if (completion.content() != null && !completion.content().isBlank()) {
                finalAssistantMessage = completion.content();
                if (streamConsumer != null) {
                    streamConsumer.accept(completion.content());
                }
            }

            // Check if AI called tools
            if (completion.toolCalls() != null && !completion.toolCalls().isEmpty()) {
                messages.add(AiMessage.assistantWithTools(completion.content(), completion.toolCalls()));

                for (AiToolCall toolCall : completion.toolCalls()) {
                    checkActive(deadline);
                    if (++executedTools > 16) throw new IllegalStateException("Local resource limit: at most 16 tool calls per request.");
                    events.accept(AiAgentEvent.of("TOOL_RUNNING", toolCall.function().name(), toolDetail(toolCall)));
                    String toolOutput = toolRegistry.executeTool(snapshotId, workspaceDir, targetFramework, toolCall);
                    events.accept(AiAgentEvent.of(toolOutput.contains("\"ERROR\"") || toolOutput.contains("\"error\"") ? "TOOL_ERROR" : "TOOL_COMPLETE", toolCall.function().name(), toolDetail(toolCall)));
                    if (toolOutput.length() > 5000) toolOutput = objectMapper.valueToTree(Map.of("truncated", true, "excerpt", toolOutput.substring(0, 4500), "hint", "Read a smaller file line range or use search_project_code.")).toString();
                    messages.add(AiMessage.tool(toolCall.id(), toolCall.function().name(), toolOutput));
                }

            } else {
                // Done tool iterations
                break;
            }
        }

        // Step 3: Sandboxed build and validation
        checkActive(deadline);
        events.accept(AiAgentEvent.of("STATIC_VALIDATION", null, "Running static checks only; no host npm scripts are executed."));
        var buildResult = sandboxedExecutionService.validateAndBuild(workspaceDir, targetFramework);

        // Step 4: Compute diffs
        List<CodeDiff> diffs = revisionManager.computeRevisionDiffs(snapshotId, draft.revisionId(), targetFramework);
        modifiedFiles = diffs.stream().map(CodeDiff::filePath).distinct().toList();

        // Calculate token usage and estimated cost ($2.50 / 1M prompt, $10 / 1M completion for gpt-4o, $0 for free models)
        double estimatedCost = 0.0;
        if (!"ollama".equals(aiProvider.getProviderId()) && !targetModel.contains(":free")) {
            estimatedCost = (totalPromptTokens * 0.0000025) + (totalCompletionTokens * 0.000010);
        }

        AiUsage usage = new AiUsage(totalPromptTokens, totalCompletionTokens, totalPromptTokens + totalCompletionTokens, estimatedCost);

        // Update draft revision record
        revisionManager.updateDraftStatus(
                snapshotId,
                draft.revisionId(),
                modifiedFiles,
                buildResult.success(),
                buildResult.stdout() + "\n" + buildResult.stderr(),
                usage
        );

        if (diffs.isEmpty()) {
            revisionManager.markRevisionStatus(snapshotId, draft.revisionId(), "ERROR");
            throw new IllegalStateException("The model did not modify any workspace files through valid tools. No successful edit is claimed.");
        }
        events.accept(AiAgentEvent.of("DRAFT_READY", null, draft.revisionId()));
        return new AiChatResponse(
                finalAssistantMessage.isBlank() ? "Draft contains " + diffs.size() + " changed file(s). Review the diff before accepting." : finalAssistantMessage,
                draft.revisionId(),
                "DRAFT",
                modifiedFiles,
                diffs,
                buildResult.success(),
                buildResult.stdout() + "\n" + buildResult.stderr(),
                usage
        );
        } catch (CancellationException e) { revisionManager.markRevisionStatus(snapshotId, draft.revisionId(), "CANCELLED"); throw e; }
        catch (RuntimeException e) { revisionManager.markRevisionStatus(snapshotId, draft.revisionId(), "ERROR"); throw e; }
    }

    private void checkActive(long deadline) {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Local AI request cancelled.");
        if (System.nanoTime() > deadline) throw new IllegalStateException("Agent request exceeded the four-minute resource limit.");
    }

    private String toolDetail(AiToolCall call) {
        try { JsonNode args = objectMapper.readTree(call.function().arguments()); return args.path("file_path").asText(call.function().name()); }
        catch (Exception ignored) { return "Validating structured tool arguments."; }
    }

    private void compactContext(List<AiMessage> messages) {
        if (messages.stream().mapToInt(item -> item.content() == null ? 0 : item.content().length()).sum() < 5500 && messages.size() < 12) return;
        int lastUser = 0;
        for (int i = 1; i < messages.size(); i++) if ("user".equals(messages.get(i).role())) lastUser = i;
        int keepFrom = messages.size();
        int groups = 0;
        for (int i = messages.size()-1; i > lastUser; i--) if (messages.get(i).toolCalls() != null && ++groups <= 2) keepFrom = i;
        if (keepFrom <= lastUser || keepFrom == messages.size()) return;
        String summary = messages.subList(lastUser+1, keepFrom).stream().filter(item -> "tool".equals(item.role()))
                .map(item -> item.name() + ": " + (item.content().contains("\"ERROR\"") ? "reported error" : "completed; reread source if needed")).reduce("", (a,b) -> a + b + "; ");
        List<AiMessage> compact = new ArrayList<>(); compact.add(messages.get(0)); compact.add(messages.get(lastUser));
        if (!summary.isBlank()) compact.add(AiMessage.assistant("Earlier tool results: " + summary.substring(0, Math.min(summary.length(), 1000))));
        compact.addAll(messages.subList(keepFrom, messages.size())); messages.clear(); messages.addAll(compact);
    }

    private String buildSystemPrompt(String targetFramework, String selectedNodeId, String selectedNodeName) {
        StringBuilder sb = new StringBuilder();
        sb.append("Ban la Senior AI Coding Agent chuyen nghiep trong he thong Layoutly.\n");
        sb.append("Nhiem vu cua ban: Doc, phan tich va truc tiep chinh sua ma nguon du an duoc sinh ra tu Figma theo yeu cau cua nguoi dung.\n\n");
        sb.append("Target framework: ").append(targetFramework).append("\n");

        if (selectedNodeId != null && !selectedNodeId.isBlank()) {
            sb.append("Context Layer Figma dang duoc chon trong canvas: Node ID [").append(selectedNodeId).append("]");
            if (selectedNodeName != null) sb.append(" - Name: ").append(selectedNodeName);
            sb.append("\n");
        }

        sb.append("\nQuy trinh bat buoc:\n");
        sb.append("1. Su dung tool 'list_project_files' hoac 'read_project_file' de xem code hien tai neu can.\n");
        sb.append("2. Su dung 'apply_code_patch' hoac 'create_project_file' de thuc hien thay doi code.\n");
        sb.append("3. Dam bao code sach, giu nguyen asset paths (assets/...) va responsive layout.\n");
        sb.append("4. Sau khi chinh sua, chay 'run_project_build' de kiem tra build syntax.\n");
        sb.append("5. Tra ve cau trả loi ngan gon, mo ta ro cac thay doi da thuc hien cho nguoi dung.\n");

        return sb.toString();
    }
}
