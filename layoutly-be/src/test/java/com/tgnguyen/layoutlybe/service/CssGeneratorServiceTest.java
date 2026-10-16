package com.tgnguyen.layoutlybe.service;

import com.tgnguyen.layoutlybe.model.ir.DesignNode;
import com.tgnguyen.layoutlybe.model.ir.DesignNodeMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CssGeneratorServiceTest {
    private final FigmaParserService parser = new FigmaParserService();
    private final CssGeneratorService generator = new CssGeneratorService();

    @Test void correctionsAreOptInAndDoNotShrinkFixedChildrenOrEmitNegativeCssGap() throws Exception {
        var tree=parser.parseDesignTree("""
          {"document":{"id":"row","type":"FRAME","layoutMode":"HORIZONTAL","itemSpacing":-20,
           "absoluteBoundingBox":{"x":0,"y":0,"width":200,"height":100},"children":[
            {"id":"first","type":"RECTANGLE","absoluteBoundingBox":{"x":0,"y":0,"width":150,"height":100}},
            {"id":"overlay","type":"RECTANGLE","layoutPositioning":"ABSOLUTE","absoluteBoundingBox":{"x":0,"y":0,"width":20,"height":20}},
            {"id":"second","type":"RECTANGLE","absoluteBoundingBox":{"x":130,"y":0,"width":150,"height":100}}
           ]}}
          """);
        assertThat(generator.generateForNode(tree,java.util.Map.of())).contains("gap: -20.0px;").doesNotContain("flex-shrink: 0;");
        String css=new CssGeneratorService(true).generateForNode(tree,java.util.Map.of());
        assertThat(css).contains("gap: 0.0px;","flex-shrink: 0;","margin-left: -20.0px;").doesNotContain("gap: -20");
        assertThat(css.substring(css.indexOf(".node-first {"),css.indexOf(".node-overlay {"))).doesNotContain("margin-left:");
        assertThat(css.substring(css.indexOf(".node-overlay {"),css.indexOf(".node-second {"))).doesNotContain("margin-left:","flex-shrink:");
    }

    @Test void verticalNegativeSpacingSkipsHiddenAndAbsoluteSiblings() throws Exception {
        var tree=parser.parseDesignTree("""
          {"document":{"id":"column","type":"FRAME","layoutMode":"VERTICAL","itemSpacing":-10,"children":[
           {"id":"hidden","type":"RECTANGLE","visible":false},
           {"id":"a","type":"RECTANGLE","absoluteBoundingBox":{"x":0,"y":0,"width":100,"height":100}},
           {"id":"b","type":"RECTANGLE","absoluteBoundingBox":{"x":0,"y":90,"width":100,"height":100}}]}}
          """);
        String css=new CssGeneratorService(true).generateForNode(tree,java.util.Map.of());
        assertThat(css.substring(css.indexOf(".node-a {"),css.indexOf(".node-b {"))).doesNotContain("margin-top:");
        assertThat(css.substring(css.indexOf(".node-b {"))).contains("margin-top: -10.0px;");
    }

    @Test void scaleConstraintsOnZeroSizedParentUseFiniteSourcePixels() throws Exception {
        var tree=parser.parseDesignTree("""
          {"document":{"id":"parent","type":"FRAME","absoluteBoundingBox":{"x":0,"y":0,"width":0,"height":0},"children":[
           {"id":"child","type":"RECTANGLE","constraints":{"horizontal":"SCALE","vertical":"SCALE"},
            "absoluteBoundingBox":{"x":10,"y":20,"width":80,"height":40}}]}}
          """);
        String css=new CssGeneratorService(true).generateForNode(tree,java.util.Map.of());
        assertThat(css.substring(css.indexOf(".node-child {"))).contains("left: 10.0px;","top: 20.0px;","width: 80.0px;","height: 40.0px;").doesNotContain("Infinity","NaN","%");
    }

    @Test void unsupportedWrappedNegativeSpacingStillUsesValidCssFallback() throws Exception {
        String raw="{\"document\":{\"id\":\"row\",\"type\":\"FRAME\",\"layoutMode\":\"HORIZONTAL\",\"itemSpacing\":-10,\"layoutWrap\":\"WRAP\"}}";
        assertThat(new CssGeneratorService(true).generateForNode(parser.parseDesignTree(raw),java.util.Map.of())).contains("gap: 0.0px;").doesNotContain("gap: -");
        assertThat(new RenderDiagnosticsService().analyze(raw).diagnostics()).extracting(RenderDiagnosticsService.Diagnostic::code).contains("WRAPPED_NEGATIVE_SPACING");
    }

    @Test void preservesGradientStopsOpacityAndSerifTitleCaseAcrossLegacyMapping() throws Exception {
        DesignNode gradient = parser.parseDesignTree("""
                {"document":{"id":"fade","type":"RECTANGLE","absoluteBoundingBox":{"x":0,"y":0,"width":1920,"height":700},
                 "fills":[{"type":"GRADIENT_LINEAR","opacity":0.5,
                   "gradientHandlePositions":[{"x":0.5,"y":0},{"x":0.5,"y":0.8},{"x":0,"y":0}],
                   "gradientStops":[{"position":0,"color":{"r":0,"g":0,"b":0,"a":0}},
                     {"position":0.75,"color":{"r":0,"g":0,"b":0,"a":1}}]}]}}
                """);
        for (String css : new String[]{generator.generateForNode(gradient, java.util.Map.of()),
                generator.generateForNode(DesignNodeMapper.toLegacy(gradient), java.util.Map.of())}) {
            assertThat(css).contains("linear-gradient(180.0000deg, rgba(0, 0, 0, 0.00) 0.0000%, rgba(0, 0, 0, 0.50) 60.0000%)");
        }
        DesignNode text = parser.parseDesignTree("""
                {"document":{"id":"title","type":"TEXT","characters":"A hiking guide",
                 "style":{"fontFamily":"Chronicle Display","textCase":"TITLE"}}}
                """);
        String css = generator.generateForNode(text, java.util.Map.of());
        assertThat(css).contains("'Chronicle Display', Georgia, 'Times New Roman', serif", "text-transform: capitalize;");
    }

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
