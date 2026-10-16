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
                        "properties", Map.of("file_path", Map.of("type", "string", "description", "Relative source file path"),
                                "start_line", Map.of("type", "integer", "description", "First line, starting at 1"),
                                "end_line", Map.of("type", "integer", "description", "Last line, at most 80 lines per call")),
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
                        "required", List.of("file_path", "target_content", "replacement_content"))
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
                "Run static syntax/structure checks only. No production build or npm scripts are executed.",
                Map.of("type", "object", "properties", Map.of())
        ));

        tools.add(createFunctionTool(
                "run_project_tests",
                "Run static checks only; real project tests require an isolated container, which is not installed.",
                Map.of("type", "object", "properties", Map.of())
        ));

        tools.add(createFunctionTool(
                "get_preview_screenshot",
                "Capture the current revision in an isolated local browser; return a local image reference. No external image upload.",
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
            validateArguments(name, args);
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Tool cancelled before execution.");

            switch (name) {
                case "list_project_files" -> {
                    List<String> files = workspaceService.listProjectFiles(workspaceDir);
                    return objectMapper.writeValueAsString(Map.of("files", files.stream().filter(path -> path.matches("(?i).*\\.(jsx?|tsx?|css|html?|json|md)$")).limit(100).toList(), "count", files.size()));
                }
                case "read_project_file" -> {
                    String filePath = args.path("file_path").asText("");
                    requireSourcePath(filePath);
                    String content = workspaceService.readProjectFile(workspaceDir, filePath);
                    String[] lines = content.split("\n", -1);
                    int start = args.path("start_line").asInt(1);
                    int end = Math.min(lines.length, args.path("end_line").asInt(start + 59));
                    if (start < 1 || start > lines.length || end < start || end-start >= 80) throw new IllegalArgumentException("Read range must contain 1–80 valid lines.");
                    String excerpt = String.join("\n", java.util.Arrays.copyOfRange(lines, start-1, end));
                    boolean truncated = end < lines.length;
                    if (excerpt.length() > 3600) { excerpt = excerpt.substring(0, 3600); truncated = true; }
                    return objectMapper.writeValueAsString(Map.of("file_path", filePath, "content", excerpt, "start_line", start, "end_line", end, "total_lines", lines.length, "truncated", truncated));
                }
                case "search_project_code" -> {
                    String query = args.path("query").asText("");
                    if (query.isBlank() || query.length() > 200) throw new IllegalArgumentException("Search query must contain 1–200 characters.");
                    Map<String, List<String>> matches = new LinkedHashMap<>();
                    workspaceService.searchProjectCode(workspaceDir, query).entrySet().stream().limit(12).forEach(entry -> matches.put(entry.getKey(), entry.getValue().stream().limit(5).map(line -> line.substring(0, Math.min(180,line.length()))).toList()));
                    return objectMapper.writeValueAsString(Map.of("query", query, "matches", matches));
                }
                case "apply_code_patch" -> {
                    String filePath = args.path("file_path").asText("");
                    String targetContent = args.has("target_content") ? args.get("target_content").asText(null) : null;
                    String replacementContent = args.path("replacement_content").asText("");

                    requireSourcePath(filePath);
                    String oldContent = workspaceService.readProjectFile(workspaceDir, filePath);
                    if (targetContent == null || targetContent.isBlank()) throw new IllegalArgumentException("Existing files require exact target_content. Read the source first; use create_project_file for new files.");

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
                    requireSourcePath(filePath);
                    if (workspaceService.listProjectFiles(workspaceDir).contains(filePath)) throw new IllegalArgumentException("File already exists. Use apply_code_patch to preserve its layout.");
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
                    java.io.File irFile = workspaceService.getProjectDirectory(snapshotId).getParent().getParent().resolve("snapshots").resolve(snapshotId).resolve("design-ir.json").toFile();
                    JsonNode node = findDesignNode(objectMapper.readTree(irFile), nodeId);
                    if (node == null) throw new IllegalArgumentException("Design node not found.");
                    var summary = objectMapper.createObjectNode();
                    for (String field : List.of("id", "name", "type", "bounds", "style", "layout", "text")) if (node.has(field)) summary.set(field, node.get(field));
                    summary.put("childCount", node.path("children").size());
                    return summary.toString();
                }
                case "get_preview_screenshot" -> {
                    if (screenshots == null) return "{\"status\":\"UNAVAILABLE\",\"message\":\"Local browser capture is not configured. No screenshot was generated.\"}";
                    return objectMapper.writeValueAsString(screenshots.capture(snapshotId, workspaceDir.getFileName().toString(), targetFramework));
                }
                case "run_project_build", "run_project_tests" -> {
                    var buildResult = sandboxedExecutionService.validateAndBuild(workspaceDir, targetFramework);
                    return objectMapper.writeValueAsString(Map.of(
                            "success", buildResult.success(),
                            "validation_type", "STATIC_ONLY",
                            "production_build_executed", false,
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
                            "validation_type", "STATIC_ONLY",
                            "diagnostics", buildResult.errors()
                    ));
                }
                default -> {
                    return objectMapper.writeValueAsString(Map.of("error", "Unknown tool: " + name));
                }
            }

        } catch (java.util.concurrent.CancellationException e) { throw e; }
        catch (Exception e) {
            try {
                return objectMapper.writeValueAsString(Map.of("status", "ERROR", "error", e.getMessage()));
            } catch (Exception ignored) {
                return "{\"status\":\"ERROR\",\"error\":\"" + e.getMessage() + "\"}";
            }
        }
    }

    private PreviewScreenshotService screenshots;
    @Autowired(required = false)
    public void setScreenshots(PreviewScreenshotService screenshots) { this.screenshots = screenshots; }

    private void requireSourcePath(String path) {
        if (!path.matches("(?i).*\\.(jsx?|tsx?|css|html?|json|md|txt)$")) throw new IllegalArgumentException("Only source text files can be edited.");
    }

    private JsonNode findDesignNode(JsonNode node, String id) {
        if (node == null) return null;
        if (id.equals(node.path("id").asText()) || id.equals(node.path("name").asText())) return node;
        for (JsonNode child : node.path("children")) { JsonNode found = findDesignNode(child, id); if (found != null) return found; }
        return null;
    }

    private void validateArguments(String name, JsonNode args) {
        if (args == null || !args.isObject()) throw new IllegalArgumentException("Tool arguments must be a JSON object.");
        Map<String, Object> definition = getToolDefinitions().stream().filter(tool -> name.equals(((Map<?,?>)tool.get("function")).get("name"))).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown workspace tool: " + name));
        JsonNode schema = objectMapper.valueToTree(((Map<?,?>)definition.get("function")).get("parameters"));
        for (JsonNode field : schema.path("required")) if (!args.has(field.asText()) || args.get(field.asText()).isNull()) throw new IllegalArgumentException("Missing required argument: " + field.asText());
        args.fields().forEachRemaining(entry -> {
            JsonNode property = schema.path("properties").path(entry.getKey());
            if (property.isMissingNode()) throw new IllegalArgumentException("Unknown argument: " + entry.getKey());
            String type = property.path("type").asText();
            if (("string".equals(type) && !entry.getValue().isTextual()) || ("integer".equals(type) && !entry.getValue().isIntegralNumber())) throw new IllegalArgumentException("Invalid type for argument: " + entry.getKey());
            if (entry.getValue().isTextual() && entry.getValue().asText().length() > 16000) throw new IllegalArgumentException("Argument exceeds the 16000-character limit.");
        });
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
