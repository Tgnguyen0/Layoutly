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
            @Value("${ai.provider:${AI_PROVIDER:auto}}") String defaultProviderConfig) {
        this.defaultProviderConfig = defaultProviderConfig != null ? defaultProviderConfig.trim().toLowerCase() : "auto";
        for (AiProvider provider : providerList) {
            providers.put(provider.getProviderId().toLowerCase(), provider);
        }
    }

    public AiProvider getProvider(String requestedProviderId) {
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
        return providers.values().stream().anyMatch(AiProvider::isConfigured);
    }

    public Map<String, Object> getStatusSummary() {
        AiProvider active = getProvider(null);
        boolean available = active.isConfigured();

        List<Map<String, Object>> providerSummaries = new ArrayList<>();
        for (AiProvider p : providers.values()) {
            Map<String, Object> pMap = new LinkedHashMap<>();
            pMap.put("id", p.getProviderId());
            pMap.put("displayName", p.getProviderDisplayName());
            pMap.put("configured", p.isConfigured());
            pMap.put("defaultModel", p.getModelName());
            pMap.put("models", p.getAvailableModels());
            providerSummaries.add(pMap);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("available", available);
        summary.put("activeProvider", active.getProviderId());
        summary.put("activeModel", active.getModelName());
        summary.put("providers", providerSummaries);
        return summary;
    }
}
