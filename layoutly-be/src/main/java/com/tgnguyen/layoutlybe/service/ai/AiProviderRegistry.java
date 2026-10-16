package com.tgnguyen.layoutlybe.service.ai;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AiProviderRegistry {

    private final Map<String, AiProvider> providers = new LinkedHashMap<>();
    private final String defaultProviderConfig;

    @Autowired
    public AiProviderRegistry(
            List<AiProvider> providerList,
            @Value("${layoutly.ai.provider:${ai.provider:${AI_PROVIDER:ollama}}}") String defaultProviderConfig) {
        this.defaultProviderConfig = defaultProviderConfig != null ? defaultProviderConfig.trim().toLowerCase() : "auto";
        for (AiProvider provider : providerList) {
            providers.put(provider.getProviderId().toLowerCase(), provider);
        }
    }

    public AiProvider getProvider(String requestedProviderId) {
        if ("ollama".equals(defaultProviderConfig)) {
            if (requestedProviderId != null && !requestedProviderId.isBlank() && !"ollama".equalsIgnoreCase(requestedProviderId)) {
                throw new IllegalArgumentException("Offline mode only allows Local Ollama. Cloud fallback is disabled.");
            }
            AiProvider local = providers.get("ollama");
            if (local == null) throw new IllegalStateException("Local Ollama provider is not registered.");
            return local;
        }
        if (requestedProviderId != null && !requestedProviderId.isBlank()) {
            String id = requestedProviderId.trim().toLowerCase();
            AiProvider p = providers.get(id);
            if (p != null) {
                return p;
            }
        }

        // Auto resolution or default config
        if (!"auto".equals(defaultProviderConfig) && providers.containsKey(defaultProviderConfig)) {
            return providers.get(defaultProviderConfig);
        }

        // Priority resolution: openrouter -> gemini -> openai
        Optional<AiProvider> configured = providers.values().stream()
                .filter(AiProvider::isConfigured)
                .findFirst();

        if (configured.isPresent()) {
            return configured.get();
        }

        // Fallback to openrouter or first registered
        return providers.getOrDefault("openrouter", providers.values().iterator().next());
    }

    public boolean isAnyProviderConfigured() {
        if ("ollama".equals(defaultProviderConfig)) return getProvider(null).isConfigured();
        return providers.values().stream().anyMatch(AiProvider::isConfigured);
    }

    public Map<String, Object> getStatusSummary() {
        AiProvider active = getProvider(null);
        boolean available = active.isConfigured();

        List<Map<String, Object>> providerSummaries = new ArrayList<>();
        for (AiProvider p : providers.values()) {
            if ("ollama".equals(defaultProviderConfig) && !"ollama".equals(p.getProviderId())) continue;
            Map<String, Object> pMap = new LinkedHashMap<>();
            pMap.put("id", p.getProviderId());
            pMap.put("displayName", p.getProviderDisplayName());
            pMap.put("configured", p.isConfigured());
            pMap.put("defaultModel", p.getModelName());
            pMap.put("models", p.modelDetails());
            pMap.putAll(p.diagnostics());
            providerSummaries.add(pMap);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("available", available);
        summary.put("activeProvider", active.getProviderId());
        summary.put("activeModel", active.getModelName());
        summary.put("providers", providerSummaries);
        summary.putAll(active.diagnostics());
        summary.put("offlineOnly", "ollama".equals(defaultProviderConfig));
        summary.put("capabilities", Map.of("jobs", true, "cancellation", true, "toolProgress", true, "streaming", false));
        return summary;
    }
}
