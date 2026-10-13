package com.tgnguyen.layoutlybe.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgnguyen.layoutlybe.model.ai.AiMessage;
import com.tgnguyen.layoutlybe.model.ai.AiToolCall;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class OpenRouterProvider implements AiProvider {

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public OpenRouterProvider(
            ObjectMapper objectMapper,
            @Value("${openrouter.api.key:${OPENROUTER_API_KEY:}}") String apiKey,
            @Value("${openrouter.model:${OPENROUTER_MODEL:google/gemini-2.0-flash-lite-preview-02-05:free}}") String model,
            @Value("${openrouter.base-url:${OPENROUTER_BASE_URL:https://openrouter.ai/api/v1}}") String baseUrl) {
        this.objectMapper = objectMapper.copy();
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = model != null && !model.isBlank() ? model.trim() : "openrouter/free";
        this.baseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl.replaceAll("/+$", "") : "https://openrouter.ai/api/v1";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public String getProviderId() {
        return "openrouter";
    }

    @Override
    public String getProviderDisplayName() {
        return "OpenRouter";
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    @Override
    public String getModelName() {
        return model;
    }

    @Override
    public List<ModelOption> getAvailableModels() {
        return List.of(
                new ModelOption("openrouter/free", "OpenRouter Auto (Free)", true, true),
                new ModelOption("google/gemma-4-31b-it:free", "Gemma 4 31B (Free)", true, true),
                new ModelOption("cohere/north-mini-code:free", "Cohere North Mini Code (Free)", true, true),
                new ModelOption("nvidia/nemotron-3-super-120b-a12b:free", "NVIDIA Nemotron 3 Super (Free)", true, true),
                new ModelOption("deepseek/deepseek-r1:free", "DeepSeek R1 (Free - No Tool Calling)", false, true),
                new ModelOption("qwen/qwen-2.5-coder-32b-instruct", "Qwen 2.5 Coder 32B", true, false),
                new ModelOption("anthropic/claude-3.5-sonnet", "Claude 3.5 Sonnet", true, false)
        );
    }

    @Override
    public boolean supportsToolCalling(String targetModel) {
        if (targetModel == null) return true;
        String m = targetModel.toLowerCase();
        return !m.contains("deepseek-r1");
    }

    @Override
    public ProviderCompletion complete(List<AiMessage> messages, Object toolDefinitions, double temperature) {
        return complete(messages, toolDefinitions, temperature, this.model);
    }

    @Override
    public ProviderCompletion complete(List<AiMessage> messages, Object toolDefinitions, double temperature, String targetModel) {
        if (!isConfigured()) {
            throw new IllegalStateException("OpenRouter API Key chưa được cấu hình ở backend environment (OPENROUTER_API_KEY).");
        }

        String activeModel = (targetModel != null && !targetModel.isBlank()) ? targetModel : this.model;

        if (!supportsToolCalling(activeModel) && toolDefinitions != null) {
            throw new IllegalStateException("Model '" + activeModel + "' không hỗ trợ Tool Calling. Vui lòng chọn model hỗ trợ Tool Calling (như Gemini 2.0 Flash Lite hoặc Qwen 2.5 Coder).");
        }

        try {
            Map<String, Object> payload = buildRequestBody(messages, toolDefinitions, temperature, false, activeModel);
            String jsonPayload = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("HTTP-Referer", "https://layoutly.app")
                    .header("X-Title", "Layoutly AI Coding Agent")
                    .timeout(Duration.ofSeconds(90))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = executeWithRetry(request);

            if (response.statusCode() != 200) {
                String sanitizedBody = response.body().replace(apiKey, "[REDACTED_API_KEY]");
                throw new RuntimeException("OpenRouter API error (" + response.statusCode() + "): " + sanitizedBody);
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choice = root.path("choices").get(0);
            JsonNode messageNode = choice.path("message");
            String finishReason = choice.path("finish_reason").asText("");

            String content = messageNode.path("content").isNull() ? null : messageNode.path("content").asText(null);

            List<AiToolCall> toolCalls = new ArrayList<>();
            if (messageNode.has("tool_calls") && messageNode.get("tool_calls").isArray()) {
                for (JsonNode toolNode : messageNode.get("tool_calls")) {
                    String id = toolNode.path("id").asText();
                    String type = toolNode.path("type").asText("function");
                    String funcName = toolNode.path("function").path("name").asText();
                    String funcArgs = toolNode.path("function").path("arguments").asText();
                    toolCalls.add(new AiToolCall(id, type, new AiToolCall.FunctionCall(funcName, funcArgs)));
                }
            }

            int promptTokens = root.path("usage").path("prompt_tokens").asInt(0);
            int completionTokens = root.path("usage").path("completion_tokens").asInt(0);

            return new ProviderCompletion(content, toolCalls, promptTokens, completionTokens, finishReason);

        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage().replace(apiKey, "[REDACTED_API_KEY]") : "Unknown error";
            throw new RuntimeException("Lỗi kết nối OpenRouter API: " + msg, e);
        }
    }

    @Override
    public void stream(List<AiMessage> messages, Object toolDefinitions, double temperature, Consumer<String> chunkConsumer) {
        stream(messages, toolDefinitions, temperature, chunkConsumer, this.model);
    }

    @Override
    public void stream(List<AiMessage> messages, Object toolDefinitions, double temperature, Consumer<String> chunkConsumer, String targetModel) {
        if (!isConfigured()) {
            throw new IllegalStateException("OpenRouter API Key chưa được cấu hình ở backend environment (OPENROUTER_API_KEY).");
        }

        String activeModel = (targetModel != null && !targetModel.isBlank()) ? targetModel : this.model;

        try {
            Map<String, Object> payload = buildRequestBody(messages, toolDefinitions, temperature, true, activeModel);
            String jsonPayload = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("HTTP-Referer", "https://layoutly.app")
                    .header("X-Title", "Layoutly AI Coding Agent")
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                String sanitizedBody = errorBody.replace(apiKey, "[REDACTED_API_KEY]");
                throw new RuntimeException("OpenRouter Streaming API error (" + response.statusCode() + "): " + sanitizedBody);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) {
                            break;
                        }
                        try {
                            JsonNode chunkNode = objectMapper.readTree(data);
                            JsonNode delta = chunkNode.path("choices").get(0).path("delta");
                            if (delta.has("content") && !delta.get("content").isNull()) {
                                chunkConsumer.accept(delta.get("content").asText());
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage().replace(apiKey, "[REDACTED_API_KEY]") : "Unknown error";
            throw new RuntimeException("Lỗi streaming từ OpenRouter API: " + msg, e);
        }
    }

    private Map<String, Object> buildRequestBody(List<AiMessage> messages, Object toolDefinitions, double temperature, boolean stream, String activeModel) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", activeModel);
        payload.put("temperature", temperature);
        if (stream) {
            payload.put("stream", true);
        }

        List<Map<String, Object>> formattedMessages = new ArrayList<>();
        for (AiMessage msg : messages) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("role", msg.role());

            if (msg.content() != null) {
                map.put("content", msg.content());
            }

            if (msg.name() != null) {
                map.put("name", msg.name());
            }

            if (msg.toolCallId() != null) {
                map.put("tool_call_id", msg.toolCallId());
            }

            if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                List<Map<String, Object>> toolCallMaps = new ArrayList<>();
                for (AiToolCall tc : msg.toolCalls()) {
                    Map<String, Object> tcMap = new LinkedHashMap<>();
                    tcMap.put("id", tc.id());
                    tcMap.put("type", tc.type());
                    tcMap.put("function", Map.of(
                            "name", tc.function().name(),
                            "arguments", tc.function().arguments()
                    ));
                    toolCallMaps.add(tcMap);
                }
                map.put("tool_calls", toolCallMaps);
            }

            formattedMessages.add(map);
        }
        payload.put("messages", formattedMessages);

        if (toolDefinitions != null && supportsToolCalling(activeModel)) {
            payload.put("tools", toolDefinitions);
        }

        return payload;
    }

    private HttpResponse<String> executeWithRetry(HttpRequest request) throws Exception {
        int maxRetries = 3;
        int attempt = 0;
        while (true) {
            try {
                attempt++;
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();
                if ((status == 429 || status == 503) && attempt <= maxRetries) {
                    Thread.sleep(1500L * attempt);
                    continue;
                }
                return response;
            } catch (Exception e) {
                if (attempt <= maxRetries) {
                    Thread.sleep(1500L * attempt);
                    continue;
                }
                throw e;
            }
        }
    }
}
