package com.tgnguyen.layoutlybe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgnguyen.layoutlybe.fixture.FixtureLoader;
import com.tgnguyen.layoutlybe.model.snapshot.FigmaSnapshot;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FigmaSnapshotServiceTest {

    @Test
    void coalescesConcurrentImportsAndReusesSnapshotForDifferentTokens() {
        FigmaService figmaService = mock(FigmaService.class);
        AssetExportService assetExportService = mock(AssetExportService.class);
        String rawJson = new FixtureLoader().loadRawJson("horizontal");
        when(figmaService.getFile(eq("file-key"), any()))
                .thenReturn(Mono.delay(Duration.ofMillis(25)).thenReturn(rawJson));

        FigmaSnapshotService service = new FigmaSnapshotService(
                figmaService,
                new FigmaParserService(),
                assetExportService,
                new ObjectMapper());

        Mono<FigmaSnapshot> first = service.importSnapshot("file-key", "token-a");
        Mono<FigmaSnapshot> second = service.importSnapshot("file-key", "token-b");
        var imported = Mono.zip(first, second).block();

        assertThat(imported).isNotNull();
        assertThat(imported.getT1().getSnapshotId()).isEqualTo(imported.getT2().getSnapshotId());
        assertThat(service.importSnapshot("file-key", "token-c").block().getSnapshotId())
                .isEqualTo(imported.getT1().getSnapshotId());
        verify(figmaService, times(1)).getFile(eq("file-key"), any());
    }

    @Test
    void refreshIsTheOnlyOperationThatFetchesFileAgain() {
        FigmaService figmaService = mock(FigmaService.class);
        AssetExportService assetExportService = mock(AssetExportService.class);
        String rawJson = new FixtureLoader().loadRawJson("horizontal");
        when(figmaService.getFile(eq("file-key"), any())).thenReturn(Mono.just(rawJson));
        when(figmaService.fetchFileFresh(eq("file-key"), any())).thenReturn(Mono.just(rawJson));

        FigmaSnapshotService service = new FigmaSnapshotService(
                figmaService,
                new FigmaParserService(),
                assetExportService,
                new ObjectMapper());
        FigmaSnapshot imported = service.importSnapshot("file-key", "token").block();

        service.requireSnapshot(imported.getSnapshotId());
        service.findByFileKey("file-key");
        verify(figmaService, times(1)).getFile(eq("file-key"), any());

        service.refresh(imported.getSnapshotId(), null).block();
        verify(figmaService, times(1)).fetchFileFresh(eq("file-key"), any());
    }
}
