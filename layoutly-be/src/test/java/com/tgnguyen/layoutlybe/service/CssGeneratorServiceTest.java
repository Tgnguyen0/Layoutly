package com.tgnguyen.layoutlybe.service;

import com.tgnguyen.layoutlybe.model.ir.DesignNode;
import com.tgnguyen.layoutlybe.model.ir.DesignNodeMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CssGeneratorServiceTest {
    private final FigmaParserService parser = new FigmaParserService();
    private final CssGeneratorService generator = new CssGeneratorService();

    @Test
    void keepsRasterizedShadowPixelsOutsideTheImageLayoutBounds() throws Exception {
        DesignNode image = parser.parseDesignTree("""
                {"document":{"id":"photo","type":"RECTANGLE","fills":[{"type":"IMAGE"}],
                 "absoluteBoundingBox":{"x":-600,"y":-56,"width":550,"height":372},
                 "absoluteRenderBounds":{"x":-640,"y":-86,"width":650,"height":472}}}
                """);
        for (String css : new String[]{generator.generateForNode(image, java.util.Map.of("photo", "photo.png")),
                generator.generateForNode(DesignNodeMapper.toLegacy(image), java.util.Map.of("photo", "photo.png"))}) {
            assertThat(css).contains(".node-photo::before {", "left: -40.0px;", "top: -30.0px;",
                    "width: 650.0px;", "height: 472.0px;", "100% 100% no-repeat;")
                    .doesNotContain("background-size: cover;");
        }
    }

    @Test
    void rendersTextStrokesOnGlyphsWithoutAddingABoundingBoxOrWrappingAutoWidthText() throws Exception {
        DesignNode text = parser.parseDesignTree("""
                {"document":{"id":"title","type":"TEXT","characters":"Contact Now",
                 "absoluteBoundingBox":{"x":0,"y":0,"width":135,"height":25},
                 "strokes":[{"type":"SOLID","color":{"r":1,"g":1,"b":1}}],"strokeWeight":0.5,
                 "style":{"fontSize":20,"textAutoResize":"WIDTH_AND_HEIGHT","textAlignHorizontal":"CENTER"}}}
                """);

        for (String css : new String[]{generator.generateForNode(text, java.util.Map.of()),
                generator.generateForNode(DesignNodeMapper.toLegacy(text), java.util.Map.of())}) {
            String rule = css.substring(css.indexOf(".node-title {"));
            assertThat(rule).contains("-webkit-text-stroke: 0.5px rgba(255, 255, 255, 1.00);")
                    .contains("white-space: pre;").contains("text-align: center;")
                    .contains("overflow: visible;").doesNotContain("border:");
        }
    }

    @Test
    void preservesIndividualCornersShadowsAndClippingWithoutRemovingShapeBorders() throws Exception {
        DesignNode shape = parser.parseDesignTree("""
                {"document":{"id":"shape","type":"RECTANGLE","clipsContent":true,
                 "rectangleCornerRadii":[0,0,0,100],
                 "strokes":[{"type":"SOLID","color":{"r":0,"g":0,"b":0}}],"strokeWeight":1,
                 "effects":[{"type":"DROP_SHADOW","visible":true,"offset":{"x":0,"y":10},
                             "radius":50,"color":{"r":0,"g":0,"b":0,"a":0.05}}]}}
                """);
        String rule = generator.generateForNode(shape, java.util.Map.of());
        assertThat(rule).contains("border-radius: 0.0px 0.0px 0.0px 100.0px;")
                .contains("box-shadow: 0.0px 10.0px 50.0px 0.0px rgba(0, 0, 0, 0.05);")
                .contains("border: 1.0px solid rgba(0, 0, 0, 1.00);");
        assertThat(rule.substring(rule.indexOf(".node-shape {"))).contains("overflow: hidden;");
    }

    @Test
    void rendersEllipsesAsCirclesAndSkipsDuplicateEffectsForRasterizedAssets() throws Exception {
        DesignNode ellipse = parser.parseDesignTree("""
                {"document":{"id":"circle","type":"ELLIPSE",
                 "effects":[{"type":"DROP_SHADOW","visible":false,"offset":{"x":0,"y":10},
                             "radius":50,"color":{"r":0,"g":0,"b":0,"a":0.05}}]}}
                """);
        assertThat(generator.generateForNode(ellipse, java.util.Map.of()))
                .contains("border-radius: 50%;").doesNotContain("box-shadow:");

        DesignNode asset = parser.parseDesignTree("""
                {"document":{"id":"icon","type":"VECTOR",
                 "strokes":[{"type":"SOLID","color":{"r":0,"g":0,"b":0}}],"strokeWeight":1,
                 "effects":[{"type":"DROP_SHADOW","offset":{"x":0,"y":10},"radius":50,
                             "color":{"r":0,"g":0,"b":0,"a":0.05}}]}}
                """);
        String css = generator.generateForNode(asset, java.util.Map.of("icon", "assets/icon.png"));
        assertThat(css.substring(css.indexOf(".node-icon {")))
                .contains("background-image:").doesNotContain("border:", "box-shadow:");
    }

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
