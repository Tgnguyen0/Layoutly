package com.tgnguyen.layoutlybe.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiProviderRegistryTest {

    private OpenAiProvider openAiProvider;
    private OpenRouterProvider openRouterProvider;
    private GeminiProvider geminiProvider;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        openAiProvider = new OpenAiProvider(objectMapper, "", "gpt-4o", "https://api.openai.com/v1");
        openRouterProvider = new OpenRouterProvider(objectMapper, "sk-or-dummy-key", "google/gemini-2.0-flash-lite-preview-02-05:free", "https://openrouter.ai/api/v1");
        geminiProvider = new GeminiProvider(objectMapper, "", "gemini-2.0-flash", "https://generativelanguage.googleapis.com/v1beta/openai");
    }

    @Test
    void testProviderRegistrationAndAutoResolution() {
        AiProviderRegistry registry = new AiProviderRegistry(
                List.of(openAiProvider, openRouterProvider, geminiProvider),
                "auto"
        );

        assertTrue(registry.isAnyProviderConfigured());
        assertEquals("openrouter", registry.getProvider(null).getProviderId());
        assertEquals("openrouter", registry.getProvider("openrouter").getProviderId());
    }

    @Test
    void testExplicitProviderSelection() {
        OpenAiProvider configuredOpenAi = new OpenAiProvider(objectMapper, "sk-proj-dummy", "gpt-4o", "https://api.openai.com/v1");
        AiProviderRegistry registry = new AiProviderRegistry(
                List.of(configuredOpenAi, openRouterProvider, geminiProvider),
                "openai"
        );

        assertEquals("openai", registry.getProvider(null).getProviderId());
        assertEquals("gemini", registry.getProvider("gemini").getProviderId());
    }

    @Test
    void testToolCallingSupportCheck() {
        assertTrue(openRouterProvider.supportsToolCalling("google/gemini-2.0-flash-lite-preview-02-05:free"));
        assertTrue(openRouterProvider.supportsToolCalling("qwen/qwen-2.5-coder-32b-instruct:free"));
        assertFalse(openRouterProvider.supportsToolCalling("deepseek/deepseek-r1:free"));
    }

    @Test
    void testUnsupportedToolCallingException() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                openRouterProvider.complete(List.of(), List.of("dummyTool"), 0.2, "deepseek/deepseek-r1:free")
        );
        assertTrue(ex.getMessage().contains("không hỗ trợ Tool Calling"));
    }

    @Test
    void testApiKeySanitizationInErrors() {
        OpenRouterProvider providerWithSecret = new OpenRouterProvider(objectMapper, "secret-super-key-12345", "google/gemini-2.0-flash-lite-preview-02-05:free", "https://openrouter.ai/api/v1");

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                providerWithSecret.complete(List.of(), null, 0.2, "google/gemini-2.0-flash-lite-preview-02-05:free")
        );

        assertFalse(ex.getMessage().contains("secret-super-key-12345"), "API Key must never be exposed in exception message");
    }
}
