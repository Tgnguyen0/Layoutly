package com.tgnguyen.layoutlybe.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class FigmaServiceCacheTest {

    @TempDir
    Path cacheDirectory;

    @Test
    void readsOnlyTokenScopedPersistedFilesAndPreservesLegacyCache() throws Exception {
        Path fileDirectory = cacheDirectory.resolve("figma-files");
        Files.createDirectories(fileDirectory);
        Files.writeString(
                fileDirectory.resolve(AccountService.digest("owner-token") + "-cached-file.json"),
                "{\"name\":\"Cached design\"}",
                StandardCharsets.UTF_8);

        WebClient webClient = mock(WebClient.class);
        FigmaService service = new FigmaService(webClient);
        ReflectionTestUtils.setField(service, "cacheDirectory", cacheDirectory.toString());

        assertThat(service.getFile("cached-file", "owner-token").block())
                .isEqualTo("{\"name\":\"Cached design\"}");
        verifyNoInteractions(webClient);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getFile("cached-file", null))
                .isInstanceOf(IllegalStateException.class);
    }
}
