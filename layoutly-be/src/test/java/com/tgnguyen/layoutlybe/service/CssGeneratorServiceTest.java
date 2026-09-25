package com.tgnguyen.layoutlybe.service;

import com.tgnguyen.layoutlybe.model.ir.DesignNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CssGeneratorServiceTest {
    private final FigmaParserService parser = new FigmaParserService();
    private final CssGeneratorService generator = new CssGeneratorService();

    @Test
    void mapsHorizontalAutoLayoutToFlexbox() throws Exception {
        DesignNode tree = parser.parseDesignTree("""
                {"document":{"id":"0","type":"DOCUMENT","children":[
                  {"id":"1","type":"CANVAS","children":[
                    {"id":"2","name":"Row","type":"FRAME","layoutMode":"HORIZONTAL",
                     "itemSpacing":10,"paddingTop":4,"paddingRight":8,"paddingBottom":4,"paddingLeft":8,
                     "primaryAxisAlignItems":"CENTER","counterAxisAlignItems":"MAX","layoutWrap":"WRAP",
                     "absoluteBoundingBox":{"x":100,"y":200,"width":400,"height":100},"children":[
                       {"id":"3","name":"Child","type":"RECTANGLE","layoutGrow":1,
                        "absoluteBoundingBox":{"x":108,"y":204,"width":100,"height":40}}
                     ]}
                  ]}
                ]}}
                """);

        String css = generator.generate(tree);
        assertThat(css).contains("display: flex;")
                .contains("flex-direction: row;")
                .contains("gap: 10.0px;")
                .contains("padding: 4.0px 8.0px 4.0px 8.0px;")
                .contains("justify-content: center;")
                .contains("align-items: flex-end;")
                .contains("flex-wrap: wrap;")
                .contains("flex: 1 1 0;");

        String childRule = css.substring(css.indexOf(".node-3"));
        assertThat(childRule).contains("position: relative;").doesNotContain("left: 8.0px;");
    }

    @Test
    void keepsAbsolutePositioningForNonAutoLayout() throws Exception {
        DesignNode tree = parser.parseDesignTree("""
                {"document":{"id":"0","type":"DOCUMENT","children":[
                  {"id":"1","type":"CANVAS","children":[
                    {"id":"2","name":"Freeform","type":"FRAME",
                     "absoluteBoundingBox":{"x":100,"y":200,"width":400,"height":300},"children":[
                       {"id":"3","name":"Child","type":"RECTANGLE",
                        "absoluteBoundingBox":{"x":125,"y":240,"width":80,"height":50}}
                     ]}
                  ]}
                ]}}
                """);

        String css = generator.generate(tree);
        String childRule = css.substring(css.indexOf(".node-3"));
        assertThat(childRule).contains("position: absolute;")
                .contains("left: 25.0px;")
                .contains("top: 40.0px;");
    }
}
