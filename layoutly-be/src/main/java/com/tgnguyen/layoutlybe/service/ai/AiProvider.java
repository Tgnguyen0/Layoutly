package com.tgnguyen.layoutlybe.service.ai;

import com.tgnguyen.layoutlybe.model.ai.AiMessage;
import com.tgnguyen.layoutlybe.model.ai.AiToolCall;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface AiProvider {

    record ProviderCompletion(
            String content,
            List<AiToolCall> toolCalls,
            int promptTokens,
            int completionTokens,
            String finishReason
    ) {}

    record ModelOption(
            String id,
            String name,
            boolean supportsTools,
            boolean isFree
    ) {}

    String getProviderId();

    String getProviderDisplayName();

    boolean isConfigured();

    String getModelName();

    List<ModelOption> getAvailableModels();

    boolean supportsToolCalling(String model);

    default Map<String, Object> diagnostics() { return Map.of(); }

    default Object modelDetails() { return getAvailableModels(); }

    ProviderCompletion complete(List<AiMessage> messages, Object toolDefinitions, double temperature, String model);

    default ProviderCompletion complete(List<AiMessage> messages, Object toolDefinitions, double temperature) {
        return complete(messages, toolDefinitions, temperature, getModelName());
    }

    void stream(List<AiMessage> messages, Object toolDefinitions, double temperature, Consumer<String> chunkConsumer, String model);

    default void stream(List<AiMessage> messages, Object toolDefinitions, double temperature, Consumer<String> chunkConsumer) {
        stream(messages, toolDefinitions, temperature, chunkConsumer, getModelName());
    }
}
