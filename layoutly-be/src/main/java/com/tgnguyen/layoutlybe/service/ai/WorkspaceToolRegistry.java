package com.tgnguyen.layoutlybe.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgnguyen.layoutlybe.model.ai.AiToolCall;
import com.tgnguyen.layoutlybe.model.ir.DesignNode;
import com.tgnguyen.layoutlybe.service.SnapshotStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class WorkspaceToolRegistry {

    private final ProjectWorkspaceService workspaceService;
    private final CodePatchEngine patchEngine;
    private final SandboxedExecutionService sandboxedExecutionService;
    private final SnapshotStorageService snapshotStorageService;
    private final ObjectMapper objectMapper;

    @Autowired
    public WorkspaceToolRegistry(
            ProjectWorkspaceService workspaceService,
            CodePatchEngine patchEngine,
            SandboxedExecutionService sandboxedExecutionService,
            SnapshotStorageService snapshotStorageService,
            ObjectMapper objectMapper) {
        this.workspaceService = workspaceService;
        this.patchEngine = patchEngine;
        this.sandboxedExecutionService = sandboxedExecutionService;
        this.snapshotStorageService = snapshotStorageService;
        this.objectMapper = objectMapper.copy();
    }

    public List<Map<String, Object>> getToolDefinitions() {
        List<Map<String, Object>> tools = new ArrayList<>();

        tools.add(createFunctionTool(
                "list_project_files",
                "Liet ke tat ca cac file ma nguon co trong workspace du an hien tai.",
                Map.of("type", "object", "properties", Map.of())
        ));

        tools.add(createFunctionTool(
                "read_project_file",
                "Doc noi dung cua mot file trong workspace du an.",
                Map.of("type", "object",
                        "properties", Map.of("file_path", Map.of("type", "string", "description", "Duong dan tuong doi toi file (vi du: src/App.jsx, src/styles/layoutly.css)")),
                        "required", List.of("file_path"))
        ));

        tools.add(createFunctionTool(
                "search_project_code",
                "Tim kiem tu khoa hoac chuoi code trong tat ca cac file cua workspace.",
                Map.of("type", "object",
                        "properties", Map.of("query", Map.of("type", "string", "description", "Tu khoa hoac chuoi code can tim")),
                        "required", List.of("query"))
        ));

        tools.add(createFunctionTool(
                "apply_code_patch",
                "Chinh sua code cua mot file bang cach thay the doan code target_content thanh replacement_content.",
                Map.of("type", "object",
                        "properties", Map.of(
                                "file_path", Map.of("type", "string", "description", "Duong dan tuong doi toi file"),
                                "target_content", Map.of("type", "string", "description", "Doan code cu can thay (de c: nguyen doan code chinh xac)"),
                                "replacement_content", Map.of("type", "string", "description", "Doan code moi thay the")
                        ),
                        "required", List.of("file_path", "replacement_content"))
        ));

        tools.add(createFunctionTool(
                "create_project_file",
                "Tao mot file moi voi noi dung duoc cung cap trong workspace.",
                Map.of("type", "object",
                        "properties", Map.of(
                                "file_path", Map.of("type", "string", "description", "Duong dan tuong doi toi file moi (vi du: src/components/Navbar.jsx)"),
                                "content", Map.of("type", "string", "description", "Noi dung cua file moi")
                        ),
                        "required", List.of("file_path", "content"))
        ));

        tools.add(createFunctionTool(
                "delete_project_file",
                "Xoa mot file khoi workspace du an.",
                Map.of("type", "object",
                        "properties", Map.of("file_path", Map.of("type", "string", "description", "Duong dan tuong doi toi file can xoa")),
                        "required", List.of("file_path"))
        ));

        tools.add(createFunctionTool(
                "inspect_design_node",
                "Truy van thong tin thiet ke Figma (DesignNode/IR) theo ID node hoac ten node.",
                Map.of("type", "object",
                        "properties", Map.of("node_id", Map.of("type", "string", "description", "ID node Figma (vi du: 1:234 hoac Frame_1)")),
                        "required", List.of("node_id"))
        ));

        tools.add(createFunctionTool(
                "run_project_build",
                "Chay kiem tra build va syntax validator tren workspace hien tai.",
                Map.of("type", "object", "properties", Map.of())
        ));

        tools.add(createFunctionTool(
                "run_project_tests",
                "Chay kiem tra unit test / lint / structure validator tren workspace.",
                Map.of("type", "object", "properties", Map.of())
        ));

        tools.add(createFunctionTool(
                "get_build_diagnostics",
                "Lay danh sach chi tiet cac loi compile hoac syntax hien tai neu build that bai.",
                Map.of("type", "object", "properties", Map.of())
        ));

        return tools;
    }

    public String executeTool(String snapshotId, Path workspaceDir, String targetFramework, AiToolCall toolCall) {
        String name = toolCall.function().name();
        String argsJson = toolCall.function().arguments();

        try {
            JsonNode args = objectMapper.readTree(argsJson == null || argsJson.isBlank() ? "{}" : argsJson);

            switch (name) {
                case "list_project_files" -> {
                    List<String> files = workspaceService.listProjectFiles(workspaceDir);
                    return objectMapper.writeValueAsString(Map.of("files", files, "count", files.size()));
                }
                case "read_project_file" -> {
                    String filePath = args.path("file_path").asText("");
                    String content = workspaceService.readProjectFile(workspaceDir, filePath);
                    return objectMapper.writeValueAsString(Map.of("file_path", filePath, "content", content));
                }
                case "search_project_code" -> {
                    String query = args.path("query").asText("");
                    Map<String, List<String>> matches = workspaceService.searchProjectCode(workspaceDir, query);
                    return objectMapper.writeValueAsString(Map.of("query", query, "matches", matches));
                }
                case "apply_code_patch" -> {
                    String filePath = args.path("file_path").asText("");
                    String targetContent = args.has("target_content") ? args.get("target_content").asText(null) : null;
                    String replacementContent = args.path("replacement_content").asText("");

                    String oldContent = "";
                    try {
                        oldContent = workspaceService.readProjectFile(workspaceDir, filePath);
                    } catch (Exception ignored) {}

                    String updatedContent = patchEngine.applyPatch(oldContent, targetContent, replacementContent);
                    workspaceService.writeProjectFile(workspaceDir, filePath, updatedContent);

                    return objectMapper.writeValueAsString(Map.of(
                            "status", "SUCCESS",
                            "file_path", filePath,
                            "message", "Da ap dung code patch thanh cong vao " + filePath
                    ));
                }
                case "create_project_file" -> {
                    String filePath = args.path("file_path").asText("");
                    String content = args.path("content").asText("");
                    workspaceService.writeProjectFile(workspaceDir, filePath, content);
                    return objectMapper.writeValueAsString(Map.of(
                            "status", "SUCCESS",
                            "file_path", filePath,
                            "message", "Da tao file moi thành cong: " + filePath
                    ));
                }
                case "delete_project_file" -> {
                    String filePath = args.path("file_path").asText("");
                    boolean deleted = workspaceService.deleteProjectFile(workspaceDir, filePath);
                    return objectMapper.writeValueAsString(Map.of(
                            "status", deleted ? "SUCCESS" : "NOT_FOUND",
                            "file_path", filePath
                    ));
                }
                case "inspect_design_node" -> {
                    String nodeId = args.path("node_id").asText("");
                    Optional<SnapshotStorageService.StoredSnapshot> stored = snapshotStorageService.load(snapshotId);
                    if (stored.isEmpty()) {
                        return objectMapper.writeValueAsString(Map.of("error", "Snapshot not found"));
                    }
                    return objectMapper.writeValueAsString(Map.of(
                            "node_id", nodeId,
                            "snapshot_id", snapshotId,
                            "info", "Figma Node ID: " + nodeId
                    ));
                }
                case "run_project_build", "run_project_tests" -> {
                    var buildResult = sandboxedExecutionService.validateAndBuild(workspaceDir, targetFramework);
                    return objectMapper.writeValueAsString(Map.of(
                            "success", buildResult.success(),
                            "exit_code", buildResult.exitCode(),
                            "stdout", buildResult.stdout(),
                            "stderr", buildResult.stderr(),
                            "errors", buildResult.errors()
                    ));
                }
                case "get_build_diagnostics" -> {
                    var buildResult = sandboxedExecutionService.validateAndBuild(workspaceDir, targetFramework);
                    return objectMapper.writeValueAsString(Map.of(
                            "build_success", buildResult.success(),
                            "diagnostics", buildResult.errors()
                    ));
                }
                default -> {
                    return objectMapper.writeValueAsString(Map.of("error", "Unknown tool: " + name));
                }
            }

        } catch (Exception e) {
            try {
                return objectMapper.writeValueAsString(Map.of("status", "ERROR", "error", e.getMessage()));
            } catch (Exception ignored) {
                return "{\"status\":\"ERROR\",\"error\":\"" + e.getMessage() + "\"}";
            }
        }
    }

    private Map<String, Object> createFunctionTool(String name, String description, Map<String, Object> parameters) {
        Map<String, Object> functionMap = new LinkedHashMap<>();
        functionMap.put("name", name);
        functionMap.put("description", description);
        functionMap.put("parameters", parameters);

        Map<String, Object> toolMap = new LinkedHashMap<>();
        toolMap.put("type", "function");
        toolMap.put("function", functionMap);
        return toolMap;
    }
}
