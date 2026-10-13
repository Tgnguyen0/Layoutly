package com.tgnguyen.layoutlybe.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgnguyen.layoutlybe.model.ai.AiChatRequest;
import com.tgnguyen.layoutlybe.model.ai.AiChatResponse;
import com.tgnguyen.layoutlybe.model.ai.AiMessage;
import com.tgnguyen.layoutlybe.model.ai.AiToolCall;
import com.tgnguyen.layoutlybe.model.ai.AiUsage;
import com.tgnguyen.layoutlybe.model.ai.CodeDiff;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class AiAgentLoopService {

    private final AiProviderRegistry providerRegistry;
    private final WorkspaceToolRegistry toolRegistry;
    private final RevisionManager revisionManager;
    private final ProjectWorkspaceService workspaceService;
    private final SandboxedExecutionService sandboxedExecutionService;
    private final ObjectMapper objectMapper;

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
        AiProvider aiProvider = providerRegistry.getProvider(request.provider());

        if (!aiProvider.isConfigured()) {
            throw new IllegalStateException("AI Provider '" + aiProvider.getProviderDisplayName() + "' chưa được cấu hình API Key ở backend environment.");
        }

        String targetModel = (request.model() != null && !request.model().isBlank())
                ? request.model() : aiProvider.getModelName();

        if (!aiProvider.supportsToolCalling(targetModel)) {
            throw new IllegalStateException("Model '" + targetModel + "' không hỗ trợ Tool Calling (Function Calling). Vui lòng chọn model hỗ trợ Tool Calling (như Gemini 2.0 Flash Lite hoặc Qwen 2.5 Coder).");
        }

        String targetFramework = (request.targetFramework() == null || request.targetFramework().isBlank())
                ? "REACT" : request.targetFramework().toUpperCase();

        // Step 1: Create draft workspace
        var draft = revisionManager.createDraftWorkspace(snapshotId, request.prompt(), targetFramework);
        Path workspaceDir = draft.workspaceDir();

        // Step 2: Build system prompt
        List<AiMessage> messages = new ArrayList<>();
        messages.add(AiMessage.system(buildSystemPrompt(targetFramework, request.selectedNodeId(), request.selectedNodeName())));

        // Append conversation history if provided
        if (request.history() != null) {
            messages.addAll(request.history());
        }

        // Add current user prompt
        messages.add(AiMessage.user(request.prompt()));

        List<Map<String, Object>> tools = toolRegistry.getToolDefinitions();

        int maxIterations = 10;
        int currentIteration = 0;
        int totalPromptTokens = 0;
        int totalCompletionTokens = 0;

        String finalAssistantMessage = "";
        List<String> modifiedFiles = new ArrayList<>();

        while (currentIteration < maxIterations) {
            currentIteration++;

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
                    if ("apply_code_patch".equals(toolCall.function().name()) || "create_project_file".equals(toolCall.function().name())) {
                        try {
                            var argsNode = objectMapper.readTree(toolCall.function().arguments());
                            String filePath = argsNode.path("file_path").asText("");
                            if (!filePath.isBlank() && !modifiedFiles.contains(filePath)) {
                                modifiedFiles.add(filePath);
                            }
                        } catch (Exception ignored) {}
                    }

                    String toolOutput = toolRegistry.executeTool(snapshotId, workspaceDir, targetFramework, toolCall);
                    messages.add(AiMessage.tool(toolCall.id(), toolCall.function().name(), toolOutput));
                }

            } else {
                // Done tool iterations
                break;
            }
        }

        // Step 3: Sandboxed build and validation
        var buildResult = sandboxedExecutionService.validateAndBuild(workspaceDir, targetFramework);

        // Step 4: Compute diffs
        List<CodeDiff> diffs = revisionManager.computeRevisionDiffs(snapshotId, draft.revisionId(), targetFramework);

        // Calculate token usage and estimated cost ($2.50 / 1M prompt, $10 / 1M completion for gpt-4o, $0 for free models)
        double estimatedCost = 0.0;
        if (!targetModel.contains(":free")) {
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

        return new AiChatResponse(
                finalAssistantMessage,
                draft.revisionId(),
                "DRAFT",
                modifiedFiles,
                diffs,
                buildResult.success(),
                buildResult.stderr(),
                usage
        );
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
