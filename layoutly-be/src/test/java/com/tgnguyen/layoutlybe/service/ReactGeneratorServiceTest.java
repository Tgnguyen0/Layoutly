package com.tgnguyen.layoutlybe.service;

import com.tgnguyen.layoutlybe.model.ir.DesignNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReactGeneratorServiceTest {
    private final FigmaParserService parser = new FigmaParserService();
    private final ReactGeneratorService generator = new ReactGeneratorService();

    @Test
    void createsNamedPageComponentAndEscapesText() throws Exception {
        DesignNode tree = sampleTree();
        var pages = generator.generatePages(tree);

        assertThat(pages).hasSize(1);
        assertThat(pages.get(0).componentName()).isEqualTo("Page01Home");
        assertThat(pages.get(0).fileName()).isEqualTo("page-01-home.jsx");
        assertThat(pages.get(0).source())
                .contains("export default function Page01Home()")
                .contains("className=\"figma-node")
                .contains("{\"Hello \\\"Layoutly\\\"\"}");
    }

    @Test
    void buildsRunnableViteProjectWithReusableAssets() throws Exception {
        DesignNode tree = sampleTree();
        ReactProjectExportService exporter = new ReactProjectExportService(generator, new CssGeneratorService());
        AssetExportService.AssetBundle assets = new AssetExportService.AssetBundle(
                Map.of("4:0", "assets/4-0.png"),
                Map.of("assets/4-0.png", new byte[]{1, 2, 3}));

        Map<String, byte[]> files = exporter.buildProject(tree, assets);
        assertThat(files.keySet()).contains(
                "package.json", "vite.config.js", "index.html", "src/main.jsx", "src/App.jsx",
                "src/pages/page-01-home.jsx", "src/styles/layoutly.css", "src/assets/4-0.png");
        assertThat(text(files, "src/App.jsx")).contains("import Page01Home").contains("<Page01Home />");
        assertThat(text(files, "src/styles/layoutly.css")).contains("url(\"../assets/4-0.png\")");
    }

    private DesignNode sampleTree() throws Exception {
        return parser.parseDesignTree("""
                {"document":{"id":"0:0","type":"DOCUMENT","children":[
                  {"id":"1:0","name":"Page","type":"CANVAS","children":[
                    {"id":"2:0","name":"01 / Home","type":"FRAME","layoutMode":"VERTICAL",
                     "absoluteBoundingBox":{"x":0,"y":0,"width":375,"height":812},"children":[
                       {"id":"3:0","name":"Title","type":"TEXT","characters":"Hello \\\"Layoutly\\\"",
                        "absoluteBoundingBox":{"x":20,"y":20,"width":200,"height":30}},
                       {"id":"4:0","name":"Photo","type":"RECTANGLE","fills":[{"type":"IMAGE"}],
                        "absoluteBoundingBox":{"x":20,"y":70,"width":200,"height":120}}
                     ]}
                  ]}
                ]}}
                """);
    }

    private String text(Map<String, byte[]> files, String path) {
        return new String(files.get(path), StandardCharsets.UTF_8);
    }
}
