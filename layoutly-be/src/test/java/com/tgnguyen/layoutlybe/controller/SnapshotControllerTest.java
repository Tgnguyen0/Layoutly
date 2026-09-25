package com.tgnguyen.layoutlybe.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgnguyen.layoutlybe.fixture.FixtureLoader;
import com.tgnguyen.layoutlybe.service.AssetExportService;
import com.tgnguyen.layoutlybe.service.CssGeneratorService;
import com.tgnguyen.layoutlybe.service.DesignStructureService;
import com.tgnguyen.layoutlybe.service.FigmaParserService;
import com.tgnguyen.layoutlybe.service.FigmaService;
import com.tgnguyen.layoutlybe.service.FigmaSnapshotService;
import com.tgnguyen.layoutlybe.service.FixtureService;
import com.tgnguyen.layoutlybe.service.HtmlGeneratorService;
import com.tgnguyen.layoutlybe.service.HtmlProjectExportService;
import com.tgnguyen.layoutlybe.service.ReactGeneratorService;
import com.tgnguyen.layoutlybe.service.ReactProjectExportService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SnapshotControllerTest {

    @Test
    void generatedOperationsReuseOneFileFetch() {
        FigmaService figmaService = mock(FigmaService.class);
        AssetExportService assetService = mock(AssetExportService.class);
        FixtureService fixtureService = mock(FixtureService.class);
        String rawJson = new FixtureLoader().loadRawJson("horizontal");
        when(figmaService.getFile(eq("file-key"), any())).thenReturn(Mono.just(rawJson));
        when(assetService.exportAssets(eq("file-key"), any(), any(com.tgnguyen.layoutlybe.model.ir.DesignNode.class)))
                .thenReturn(Mono.just(new AssetExportService.AssetBundle(Map.of(), Map.of())));
        when(assetService.getPreviewImageUrls(eq("file-key"), any(), any(com.tgnguyen.layoutlybe.model.ir.DesignNode.class)))
                .thenReturn(Mono.just(Map.of()));

        FigmaSnapshotService snapshotService = new FigmaSnapshotService(
                figmaService,
                new FigmaParserService(),
                assetService,
                new ObjectMapper());
        DesignStructureService structureService = new DesignStructureService();
        HtmlGeneratorService htmlGenerator = new HtmlGeneratorService();
        CssGeneratorService cssGenerator = new CssGeneratorService();
        ReactGeneratorService reactGenerator = new ReactGeneratorService();
        HtmlProjectExportService htmlExporter = new HtmlProjectExportService(htmlGenerator, cssGenerator);
        ReactProjectExportService reactExporter = new ReactProjectExportService(reactGenerator, cssGenerator);
        SnapshotController controller = new SnapshotController(
                snapshotService,
                structureService,
                htmlGenerator,
                cssGenerator,
                reactGenerator,
                htmlExporter,
                reactExporter,
                fixtureService);

        var imported = controller.importFigma(
                new SnapshotController.ImportRequest("file-key", null), "token").block();
        assertThat(imported).isNotNull();
        String id = imported.snapshotId();

        controller.getSnapshot(id);
        controller.getTree(id);
        controller.getStructure(id);
        controller.getHtml(id);
        controller.getCss(id);
        controller.getReact(id);
        String preview = controller.getPreview(id, 1440).block();
        assertThat(preview)
                .contains("<title>Horizontal Row</title>")
                .contains("data-figma-name=\"Horizontal Row\"");
        controller.getPreview(id, 1440).block();
        controller.export(id, "HTML", "AUTO").block();
        controller.export(id, "REACT", "AUTO").block();

        verify(figmaService, times(1)).getFile(eq("file-key"), any());
        verify(assetService, times(1)).exportAssets(eq("file-key"), any(), any(com.tgnguyen.layoutlybe.model.ir.DesignNode.class));
        verify(assetService, times(1)).getPreviewImageUrls(eq("file-key"), any(), any(com.tgnguyen.layoutlybe.model.ir.DesignNode.class));
    }

    @Test
    void previewPrefersViewportSizedScreenOverPresentationCover() {
        FigmaService figmaService = mock(FigmaService.class);
        AssetExportService assetService = mock(AssetExportService.class);
        FixtureService fixtureService = mock(FixtureService.class);
        String rawJson = """
                {
                  "name":"Viewport fixture","version":"v1","document":{
                    "id":"0:0","name":"Document","type":"DOCUMENT","children":[{
                      "id":"1:0","name":"Page","type":"CANVAS","children":[{
                        "id":"2:0","name":"Cover","type":"FRAME",
                        "absoluteBoundingBox":{"x":0,"y":0,"width":77001,"height":9276},
                        "children":[{
                          "id":"3:0","name":"Desktop Landing","type":"FRAME",
                          "absoluteBoundingBox":{"x":100,"y":100,"width":1440,"height":1200},
                          "children":[]
                        }]
                      }]
                    }]
                  }
                }
                """;
        when(figmaService.getFile(eq("viewport-file"), any())).thenReturn(Mono.just(rawJson));
        when(assetService.getPreviewImageUrls(
                eq("viewport-file"), any(), any(com.tgnguyen.layoutlybe.model.ir.DesignNode.class)))
                .thenReturn(Mono.just(Map.of()));

        FigmaSnapshotService snapshotService = new FigmaSnapshotService(
                figmaService, new FigmaParserService(), assetService, new ObjectMapper());
        HtmlGeneratorService htmlGenerator = new HtmlGeneratorService();
        CssGeneratorService cssGenerator = new CssGeneratorService();
        ReactGeneratorService reactGenerator = new ReactGeneratorService();
        SnapshotController controller = new SnapshotController(
                snapshotService,
                new DesignStructureService(),
                htmlGenerator,
                cssGenerator,
                reactGenerator,
                new HtmlProjectExportService(htmlGenerator, cssGenerator),
                new ReactProjectExportService(reactGenerator, cssGenerator),
                fixtureService);

        var imported = controller.importFigma(
                new SnapshotController.ImportRequest("viewport-file", null), "token").block();
        assertThat(imported).isNotNull();

        assertThat(controller.getPreview(imported.snapshotId(), 1440).block())
                .contains("<title>Desktop Landing</title>")
                .doesNotContain("<title>Cover</title>");
    }
}
