package com.tgnguyen.layoutlybe.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgnguyen.layoutlybe.model.ai.AiMessage;
import com.tgnguyen.layoutlybe.model.ai.AiToolCall;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Service
public class OllamaAiProvider implements AiProvider {
    private final ObjectMapper mapper;
    private final URI base;
    private final String model;
    private final int contextWindow;
    private final int maxOutput;
    private final int timeoutSeconds;
    private final HttpClient client;
    private volatile List<Map<String, Object>> models = List.of();
    private volatile Map<String, Object> status = Map.of("providerReachable", false, "modelReady", false);
    private volatile long discoveredAt;
    private final Set<String> verifiedTools = ConcurrentHashMap.newKeySet();
    private final Map<String, Map<String, Object>> inferenceMetrics = new ConcurrentHashMap<>();
    private final Semaphore inferenceSlot = new Semaphore(1);

    public OllamaAiProvider(ObjectMapper mapper,
            @Value("${layoutly.ai.ollama.base-url:http://127.0.0.1:11434}") String baseUrl,
            @Value("${layoutly.ai.ollama.model:qwen3:4b}") String model,
            @Value("${layoutly.ai.ollama.context-window:4096}") int contextWindow,
            @Value("${layoutly.ai.ollama.max-output-tokens:1024}") int maxOutput,
            @Value("${layoutly.ai.ollama.timeout-seconds:120}") int timeoutSeconds) {
        this.mapper = mapper.copy();
        this.base = URI.create(baseUrl.replaceAll("/+$", ""));
        if (!"http".equals(base.getScheme()) || !Set.of("127.0.0.1", "localhost", "[::1]", "::1").contains(base.getHost())
                || base.getUserInfo() != null || base.getQuery() != null || !base.getPath().isEmpty()) {
            throw new IllegalArgumentException("Ollama must use a loopback HTTP URL; remote AI endpoints are forbidden.");
        }
        this.model = model;
        this.contextWindow = Math.max(2048, Math.min(4096, contextWindow));
        this.maxOutput = Math.max(128, Math.min(1536, maxOutput));
        this.timeoutSeconds = Math.max(5, Math.min(180, timeoutSeconds));
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER)
                .proxy(new ProxySelector() {
                    public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
                    public void connectFailed(URI uri, SocketAddress address, IOException error) { }
                }).build();
    }

    public String getProviderId() { return "ollama"; }
    public String getProviderDisplayName() { return "Local Ollama"; }
    public boolean isConfigured() { return true; } // No key is required; reachability is reported separately.
    public String getModelName() { return model; }
    public List<ModelOption> getAvailableModels() {
        discover();
        return models.stream().map(m -> new ModelOption((String)m.get("id"), (String)m.get("name"), Boolean.TRUE.equals(m.get("supportsTools")), true)).toList();
    }
    public Object modelDetails() { discover(); return models; }
    public Map<String, Object> diagnostics() { discover(); return status; }
    public boolean supportsToolCalling(String requested) {
        discover();
        return models.stream().anyMatch(item -> requested.equals(item.get("id")) && Boolean.TRUE.equals(item.get("supportsTools")) && Boolean.TRUE.equals(item.get("available")));
    }

    public synchronized void refreshDiscovery() { discoveredAt = 0; discover(); }

    private synchronized void discover() {
        if (System.currentTimeMillis() - discoveredAt < 2500) return;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("baseUrl", base.toString()); result.put("contextWindow", contextWindow);
        result.put("maxOutputTokens", maxOutput); result.put("cloudFallback", false);
        try {
            JsonNode tags = exchange("/api/tags", null, 3);
            JsonNode running = exchange("/api/ps", null, 3);
            List<Map<String, Object>> found = new ArrayList<>();
            for (JsonNode tag : tags.path("models")) {
                String id = tag.path("name").asText();
                boolean cloud = id.contains(":cloud") || id.endsWith("-cloud") || tag.hasNonNull("remote_host");
                long size = tag.path("size").asLong();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", id); item.put("name", id); item.put("sizeBytes", size);
                item.put("provider", "Local Ollama"); item.put("isFree", true);
                boolean loaded = false;
                for (JsonNode active : running.path("models")) if (id.equals(active.path("name").asText())) loaded = true;
                boolean tools = false;
                String error = "";
                if (!cloud && size <= 5_000_000_000L) {
                    try {
                        JsonNode details = exchange("/api/show", Map.of("model", id), 3);
                        for (JsonNode capability : details.path("capabilities")) if ("tools".equals(capability.asText())) tools = true;
                    } catch (RuntimeException e) { error = e.getMessage(); }
                } else error = cloud ? "Cloud model is forbidden in offline mode." : "Model exceeds the 5GB resource limit.";
                item.put("available", !cloud && size <= 5_000_000_000L && error.isBlank());
                item.put("supportsTools", tools); item.put("toolsVerified", verifiedTools.contains(id));
                item.put("ready", loaded && error.isBlank()); item.put("state", !error.isBlank() ? "Error" : loaded ? "Ready" : "Not Loaded");
                item.put("diagnostics", error);
                if (inferenceMetrics.containsKey(id)) item.put("lastInference", inferenceMetrics.get(id));
                found.add(item);
            }
            models = List.copyOf(found);
            Map<String, Object> selected = found.stream().filter(item -> model.equals(item.get("id"))).findFirst().orElse(null);
            result.put("providerReachable", true); result.put("modelInstalled", selected != null);
            result.put("modelReady", selected != null && Boolean.TRUE.equals(selected.get("ready")));
            result.put("available", selected != null && Boolean.TRUE.equals(selected.get("available")) && Boolean.TRUE.equals(selected.get("supportsTools")));
            result.put("message", selected == null ? "Ollama reachable; configured model is not installed: " + model
                    : "Local Ollama · " + model + " · " + selected.get("state"));
        } catch (RuntimeException e) {
            models = List.of();
            result.put("available", false); result.put("providerReachable", false); result.put("modelReady", false);
            result.put("message", "Ollama is unavailable at " + base + ". Start the local service. " + e.getMessage());
        }
        status = Collections.unmodifiableMap(result);
        discoveredAt = System.currentTimeMillis();
    }

    public Map<String, Object> loadModel(String requested) {
        refreshDiscovery();
        if (models.stream().noneMatch(item -> requested.equals(item.get("id")) && Boolean.TRUE.equals(item.get("available"))))
            throw new IllegalArgumentException("Local model is not installed or unavailable: " + requested);
        if (!inferenceSlot.tryAcquire()) throw new IllegalStateException("Local inference is busy; wait or cancel the active request.");
        try {
            unloadOtherModels(requested);
            exchange("/api/chat", Map.of("model", requested, "messages", List.of(), "stream", false, "keep_alive", "1m", "options", Map.of("num_ctx", contextWindow)), timeoutSeconds);
            refreshDiscovery();
            return models.stream().filter(item -> requested.equals(item.get("id"))).findFirst().orElseThrow();
        } finally { inferenceSlot.release(); }
    }

    private void unloadOtherModels(String requested) {
        JsonNode running = exchange("/api/ps", null, 3);
        for (JsonNode item : running.path("models")) {
            String name = item.path("name").asText();
            if (!name.equals(requested)) exchange("/api/generate", Map.of("model", name, "keep_alive", 0), 15);
        }
    }

    public ProviderCompletion complete(List<AiMessage> messages, Object tools, double temperature, String requested) {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Local AI request cancelled.");
        if (!supportsToolCalling(requested)) throw new IllegalStateException("Local model is missing, unavailable, or does not advertise native tool calling: " + requested);
        if (!inferenceSlot.tryAcquire()) throw new IllegalStateException("Local inference is already processing a request.");
        try {
            unloadOtherModels(requested);
            List<Map<String, Object>> formatted = new ArrayList<>();
            for (AiMessage message : messages) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("role", message.role()); row.put("content", Objects.toString(message.content(), ""));
                if (message.name() != null) row.put("tool_name", message.name());
                if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
                    List<Map<String, Object>> calls = new ArrayList<>();
                    for (AiToolCall call : message.toolCalls()) calls.add(Map.of("function", Map.of("name", call.function().name(), "arguments", mapper.readTree(call.function().arguments()))));
                    row.put("tool_calls", calls);
                }
                formatted.add(row);
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", requested); payload.put("messages", formatted); payload.put("stream", false);
            payload.put("think", false); payload.put("keep_alive", "1m");
            payload.put("options", Map.of("temperature", temperature, "num_ctx", contextWindow, "num_predict", maxOutput, "seed", 42));
            if (tools != null) payload.put("tools", tools);
            if (mapper.writeValueAsString(payload).length() > (contextWindow - maxOutput) * 4)
                throw new IllegalArgumentException("CONTEXT_LIMIT: request exceeds the configured local context budget. Read a smaller line range or select a smaller node.");
            long started = System.nanoTime();
            JsonNode root = exchange("/api/chat", payload, timeoutSeconds);
            JsonNode answer = root.path("message");
            List<AiToolCall> calls = new ArrayList<>();
            int index = 0;
            for (JsonNode call : answer.path("tool_calls")) {
                JsonNode args = call.path("function").path("arguments");
                if (args.isTextual()) args = mapper.readTree(args.asText());
                if (!args.isObject()) throw new IllegalArgumentException("Invalid tool arguments: expected a JSON object.");
                String name = call.path("function").path("name").asText();
                if (name.isBlank()) throw new IllegalArgumentException("Invalid tool call: missing function name.");
                calls.add(new AiToolCall("ollama_" + UUID.randomUUID() + "_" + index++, "function", new AiToolCall.FunctionCall(name, mapper.writeValueAsString(args))));
            }
            if (!calls.isEmpty()) verifiedTools.add(requested);
            inferenceMetrics.put(requested, Map.of("durationMs", (System.nanoTime() - started)/1_000_000,
                    "promptTokens", root.path("prompt_eval_count").asInt(), "outputTokens", root.path("eval_count").asInt(), "target", base + "/api/chat"));
            discoveredAt = 0;
            return new ProviderCompletion(answer.path("content").asText(""), calls, root.path("prompt_eval_count").asInt(), root.path("eval_count").asInt(), root.path("done_reason").asText("stop"));
        } catch (IllegalStateException | IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("Invalid local Ollama response: " + e.getMessage(), e); }
        finally { inferenceSlot.release(); }
    }

    // The current Layoutly HTTP contract is job-based, not token streaming.
    public void stream(List<AiMessage> messages, Object tools, double temperature, Consumer<String> consumer, String requested) {
        throw new UnsupportedOperationException("Token streaming is not exposed by the current Layoutly job contract.");
    }

    private JsonNode exchange(String path, Object body, int timeout) {
        CompletableFuture<HttpResponse<String>> future = null;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(timeout)).header("Content-Type", "application/json");
            if (body == null) builder.GET(); else builder.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            future = client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> response = future.get(timeout + 1L, TimeUnit.SECONDS);
            if (response.statusCode() != 200) {
                String message = response.body().length() > 600 ? response.body().substring(0,600) : response.body();
                throw new IllegalStateException("Ollama HTTP " + response.statusCode() + ": " + message);
            }
            return mapper.readTree(response.body());
        } catch (InterruptedException e) {
            if (future != null) future.cancel(true);
            Thread.currentThread().interrupt();
            throw new CancellationException("Local inference cancelled; HTTP connection closed.");
        } catch (TimeoutException e) {
            if (future != null) future.cancel(true);
            throw new IllegalStateException("Local inference timed out after " + timeout + " seconds.", e);
        } catch (IllegalStateException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("Local Ollama request failed: " + e.getMessage(), e); }
    }
}
