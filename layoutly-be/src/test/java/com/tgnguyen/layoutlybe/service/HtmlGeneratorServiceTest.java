package com.tgnguyen.layoutlybe.service;

import com.tgnguyen.layoutlybe.model.ir.DesignNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlGeneratorServiceTest {
    @Test
    void exportsASelectedCanvasOrFrameWithoutLosingItsRootOrStyles() throws Exception {
        DesignNode canvas = new FigmaParserService().parseDesignTree("""
                {"document":{"id":"canvas","name":"Page","type":"CANVAS","children":[
                 {"id":"frame","name":"Studio","type":"FRAME","clipsContent":true,
                  "absoluteBoundingBox":{"x":-720,"y":-2051,"width":1440,"height":4102},
                  "fills":[{"type":"SOLID","color":{"r":1,"g":1,"b":1}}],"children":[
                    {"id":"text","type":"TEXT","characters":"Contact Now",
                     "absoluteBoundingBox":{"x":-600,"y":-2000,"width":135,"height":25}}
                  ]}
                ]}}
                """);
        HtmlGeneratorService html = new HtmlGeneratorService();
        CssGeneratorService css = new CssGeneratorService();
        DesignNode frame = canvas.getChildren().get(0);
        assertThat(html.generateAuto(canvas)).hasSize(1);
        assertThat(html.generateAuto(frame)).hasSize(1);
        assertThat(html.generate(frame)).contains("data-figma-name=\"Studio\"", "Contact Now");
        assertThat(css.generate(frame)).contains(".node-frame {", "background-color: rgba(255, 255, 255, 1.00);");
        byte[] zip = new HtmlProjectExportService(html, css).export(canvas,
                new AssetExportService.AssetBundle(Map.of(), Map.of()), "AUTO");
        assertThat(zip).isNotEmpty();
    }
}
