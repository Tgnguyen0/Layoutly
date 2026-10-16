package com.tgnguyen.layoutlybe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgnguyen.layoutlybe.fixture.FixtureLoader;
import com.tgnguyen.layoutlybe.model.snapshot.FigmaSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.nio.file.Path;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FigmaSnapshotServiceTest {
    @TempDir Path directory;
    private static final String IMAGE_DESIGN = """
            {"name":"Travel","version":"v1","document":{"id":"0:0","type":"DOCUMENT","children":[
              {"id":"0:1","type":"CANVAS","children":[{"id":"1:2","name":"Travel","type":"FRAME",
              "absoluteBoundingBox":{"x":0,"y":0,"width":1920,"height":4600},"children":[
              {"id":"1:3","name":"Hero photo","type":"RECTANGLE",
               "absoluteBoundingBox":{"x":0,"y":0,"width":1920,"height":1200},
               "fills":[{"type":"IMAGE","imageRef":"photo","scaleMode":"FILL"}]}]}]}]}}
            """;

    @Test void emptyRestCacheDownloadsAndPersistsPreviewAssetsAcrossRestart() {
        var figma = mock(FigmaService.class);
        var assets = mock(AssetExportService.class);
        var storage = new SnapshotStorageService(new ObjectMapper(), directory);
        when(figma.getFile(eq("travel"), any())).thenReturn(Mono.just(IMAGE_DESIGN));
        var service = new FigmaSnapshotService(figma, new FigmaParserService(), assets, new ObjectMapper(), storage);
        var imported = service.importSnapshot("travel", "test-token").block();
        assertThat(imported.getImageNodeIds()).contains("1:3");
        assertThat(storage.load(imported.getSnapshotId()).orElseThrow().assets()).isNull();
        var bytes = new byte[]{1, 2, 3, 4};
        when(assets.exportAssets(eq("travel"), any(), any(com.tgnguyen.layoutlybe.model.ir.DesignNode.class)))
                .thenReturn(Mono.just(new AssetExportService.AssetBundle(Map.of("1:3", "assets/1-3.png"), Map.of("assets/1-3.png", bytes))));
        service = new FigmaSnapshotService(figma, new FigmaParserService(), assets, new ObjectMapper(), storage);
        var restored = service.requireSnapshot(imported.getSnapshotId());
        assertThat(service.getPreviewImageUrls(restored.getSnapshotId(), restored.getDesignIr()).block().get("1:3"))
                .isEqualTo("/api/snapshots/" + restored.getSnapshotId() + "/assets/1-3.png");
        var restarted = new FigmaSnapshotService(figma, new FigmaParserService(), assets, new ObjectMapper(), storage);
        assertThat(restarted.getAssets(restored.getSnapshotId()).block().zipAssetByPath().get("assets/1-3.png")).isEqualTo(bytes);
        verify(assets, times(1)).exportAssets(eq("travel"), any(), any(com.tgnguyen.layoutlybe.model.ir.DesignNode.class));
    }

    @Test void reusesOnlyAssetsFromTheSameFileAndVersion() {
        var figma = mock(FigmaService.class);
        var assets = mock(AssetExportService.class);
        var storage = new SnapshotStorageService(new ObjectMapper(), directory);
        when(figma.getFile(eq("travel"), any())).thenReturn(Mono.just(IMAGE_DESIGN));
        var service = new FigmaSnapshotService(figma, new FigmaParserService(), assets, new ObjectMapper(), storage);
        var older = service.importSnapshot("travel", "1:2", "test-token").block();
        var bundle = new AssetExportService.AssetBundle(Map.of("1:3", "assets/1-3.png"), Map.of("assets/1-3.png", new byte[]{1, 2}));
        older.cacheAssets(bundle); storage.saveAssets(older, bundle);
        assertThat(storage.findReusableAssets("travel", "v2", older.getImageNodeIds())).isEmpty();
        assertThat(storage.findReusableAssets("another-file", "v1", older.getImageNodeIds())).isEmpty();
        var latest = service.importSnapshot("travel", "0:0", "test-token").block();
        assertThat(latest.getSnapshotId()).isNotEqualTo(older.getSnapshotId());
        assertThat(service.getAssets(latest.getSnapshotId()).block().cssUrlByNodeId()).containsKey("1:3");
        verify(assets, times(0)).exportAssets(any(), any(), any(com.tgnguyen.layoutlybe.model.ir.DesignNode.class));
    }

    @Test void assetErrorsDoNotBecomeASuccessfulBlankPreview() {
        var figma = mock(FigmaService.class);
        var assets = mock(AssetExportService.class);
        when(figma.getFile(eq("travel"), any())).thenReturn(Mono.just(IMAGE_DESIGN));
        when(assets.exportAssets(eq("travel"), any(), any(com.tgnguyen.layoutlybe.model.ir.DesignNode.class)))
                .thenReturn(Mono.error(new IllegalStateException("Image download unavailable")));
        var service = new FigmaSnapshotService(figma, new FigmaParserService(), assets, new ObjectMapper(), new SnapshotStorageService(new ObjectMapper(), directory));
        var snapshot = service.importSnapshot("travel", "test-token").block();
        assertThatThrownBy(() -> service.getPreviewImageUrls(snapshot.getSnapshotId(), snapshot.getDesignIr()).block())
                .hasMessageContaining("Image download unavailable");
    }

    @Test void importingAgainWithATokenRetriesOnlyMissingAssets() {
        var figma = mock(FigmaService.class);
        var assets = mock(AssetExportService.class);
        var storage = new SnapshotStorageService(new ObjectMapper(), directory);
        when(figma.getFile(eq("travel"), any())).thenReturn(Mono.just(IMAGE_DESIGN));
        var service = new FigmaSnapshotService(figma, new FigmaParserService(), assets, new ObjectMapper(), storage);
        var snapshot = service.importSnapshot("travel", "test-token").block();
        snapshot.cacheAssets(new AssetExportService.AssetBundle(Map.of("existing", "assets/old.png"), Map.of("assets/old.png", new byte[]{1})));
        when(assets.exportMissingAssets(eq("travel"), eq("retry-token"), any(), eq(java.util.Set.of("1:3"))))
                .thenReturn(Mono.just(new AssetExportService.AssetBundle(Map.of("1:3", "assets/1-3.png"), Map.of("assets/1-3.png", new byte[]{2}))));
        assertThat(service.importSnapshot("travel", "retry-token").block().getSnapshotId()).isEqualTo(snapshot.getSnapshotId());
        assertThat(snapshot.getAssets().cssUrlByNodeId()).containsKeys("existing", "1:3");
        assertThat(service.missingPreviewAssets(snapshot.getSnapshotId(), snapshot.getDesignIr())).isZero();
        verify(assets, times(1)).exportMissingAssets(eq("travel"), eq("retry-token"), any(), eq(java.util.Set.of("1:3")));
    }

    @Test void selectedNodeImportReusesItsPersistentIdentityAfterRestart() {
        var figma = mock(FigmaService.class);
        var assets = mock(AssetExportService.class);
        var storage = new SnapshotStorageService(new ObjectMapper(), directory);
        String raw = new FixtureLoader().loadRawJson("horizontal");
        when(figma.getFile(eq("file-key"), any())).thenReturn(Mono.just(raw));
        var service = new FigmaSnapshotService(figma, new FigmaParserService(), assets, new ObjectMapper(), storage);
        var selected = service.importSnapshot("file-key", "1:0", "token").block();
        var full = service.importSnapshot("file-key", "token").block();
        var restarted = new FigmaSnapshotService(figma, new FigmaParserService(), assets, new ObjectMapper(), storage);
        assertThat(restarted.importSnapshot("file-key", "1:0", "token").block().getSnapshotId()).isEqualTo(selected.getSnapshotId());
        assertThat(restarted.importSnapshot("file-key", "token").block().getSnapshotId()).isEqualTo(full.getSnapshotId());
        verify(figma, times(2)).getFile(eq("file-key"), any());
    }

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
