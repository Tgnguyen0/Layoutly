package com.tgnguyen.layoutlybe.service;

import com.tgnguyen.layoutlybe.model.ir.Alignment;
import com.tgnguyen.layoutlybe.model.ir.DesignNode;
import com.tgnguyen.layoutlybe.model.ir.Direction;
import com.tgnguyen.layoutlybe.model.ir.LayoutType;
import com.tgnguyen.layoutlybe.model.ir.SizingMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FigmaParserServiceTest {
    private final FigmaParserService parser = new FigmaParserService();

    @Test
    void rotatesIndividualCornersWithTheRestBoundingBox() throws Exception {
        DesignNode halfTurn = parser.parseDesignTree("""
                {"document":{"id":"a","type":"RECTANGLE","rotation":-3.1415925661670165,
                 "rectangleCornerRadii":[0,0,100,0]}}
                """);
        DesignNode quarterTurn = parser.parseDesignTree("""
                {"document":{"id":"b","type":"RECTANGLE","rotation":1.5707963148700159,
                 "rectangleCornerRadii":[0,0,100,0]}}
                """);
        assertThat(halfTurn.getStyle().getCornerRadii()).containsExactly(100.0, 0.0, 0.0, 0.0);
        assertThat(quarterTurn.getStyle().getCornerRadii()).containsExactly(0.0, 0.0, 0.0, 100.0);
    }

    @Test
    void preservesVisualPropertiesAndPaintOpacity() throws Exception {
        DesignNode node = parser.parseDesignTree("""
                {"document":{"id":"card","type":"RECTANGLE","visible":false,"clipsContent":false,
                 "rectangleCornerRadii":[0,100,0,100],
                 "fills":[{"type":"SOLID","opacity":0.5,"color":{"r":1,"g":0,"b":0,"a":0.5}}],
                 "style":{"textAlignHorizontal":"RIGHT","textAutoResize":"HEIGHT","fontStyle":"Italic"},
                 "effects":[{"type":"INNER_SHADOW","offset":{"x":2,"y":3},"radius":4,"spread":5,
                             "color":{"r":0,"g":0,"b":0,"a":0.2}},
                            {"type":"DROP_SHADOW","visible":false}]}}
                """);
        assertThat(node.getStyle().getCornerRadii()).containsExactly(0.0, 100.0, 0.0, 100.0);
        assertThat(node.getStyle().getBackgroundColor()).isEqualTo("rgba(255, 0, 0, 0.25)");
        assertThat(node.getStyle().getTextAlign()).isEqualTo("RIGHT");
        assertThat(node.getStyle().getTextAutoResize()).isEqualTo("HEIGHT");
        assertThat(node.getStyle().getFontStyle()).isEqualTo("Italic");
        assertThat(node.getStyle().getVisible()).isFalse();
        assertThat(node.getStyle().getClipsContent()).isFalse();
        assertThat(node.getStyle().getShadows()).hasSize(1);
        assertThat(node.getStyle().getShadows().get(0).inset()).isTrue();
    }

    @Test
    void parsesHorizontalAutoLayoutAndChildSizing() throws Exception {
        DesignNode root = parser.parseDesignTree("""
                {
                  "document": {
                    "id": "0:0", "name": "Document", "type": "DOCUMENT",
                    "children": [{
                      "id": "1:0", "name": "Page", "type": "CANVAS",
                      "children": [{
                        "id": "2:0", "name": "Toolbar", "type": "FRAME",
                        "layoutMode": "HORIZONTAL", "itemSpacing": 12,
                        "paddingTop": 8, "paddingRight": 16,
                        "paddingBottom": 8, "paddingLeft": 16,
                        "primaryAxisAlignItems": "SPACE_BETWEEN",
                        "counterAxisAlignItems": "CENTER", "layoutWrap": "WRAP",
                        "layoutSizingHorizontal": "FILL", "layoutSizingVertical": "HUG",
                        "absoluteBoundingBox": {"x": 20, "y": 30, "width": 600, "height": 80},
                        "children": [{
                          "id": "3:0", "name": "Action", "type": "RECTANGLE",
                          "layoutGrow": 1, "layoutAlign": "STRETCH",
                          "absoluteBoundingBox": {"x": 36, "y": 38, "width": 100, "height": 64}
                        }]
                      }]
                    }]
                  }
                }
                """);

        DesignNode toolbar = root.getChildren().get(0).getChildren().get(0);
        DesignNode action = toolbar.getChildren().get(0);
        assertThat(toolbar.getLayout().getType()).isEqualTo(LayoutType.AUTO_FLEX);
        assertThat(toolbar.getLayout().getDirection()).isEqualTo(Direction.ROW);
        assertThat(toolbar.getLayout().getGap()).isEqualTo(12);
        assertThat(toolbar.getLayout().getPadding().left()).isEqualTo(16);
        assertThat(toolbar.getLayout().getMainAxisAlignment()).isEqualTo(Alignment.SPACE_BETWEEN);
        assertThat(toolbar.getLayout().getCrossAxisAlignment()).isEqualTo(Alignment.CENTER);
        assertThat(toolbar.getLayout().isWrap()).isTrue();
        assertThat(toolbar.getSizing().getHorizontal()).isEqualTo(SizingMode.FILL);
        assertThat(toolbar.getSizing().getVertical()).isEqualTo(SizingMode.HUG);
        assertThat(action.getSizing().getHorizontal()).isEqualTo(SizingMode.FILL);
        assertThat(action.getSizing().getVertical()).isEqualTo(SizingMode.STRETCH);
    }

    @Test
    void parsesVerticalAutoLayoutAndLegacyHugSizing() throws Exception {
        DesignNode root = parser.parseDesignTree("""
                {"document":{"id":"0:0","type":"DOCUMENT","children":[
                  {"id":"1:0","type":"CANVAS","children":[
                    {"id":"2:0","name":"Card","type":"FRAME","layoutMode":"VERTICAL",
                     "primaryAxisSizingMode":"AUTO","counterAxisSizingMode":"FIXED",
                     "absoluteBoundingBox":{"x":0,"y":0,"width":320,"height":500}}
                  ]}
                ]}}
                """);

        DesignNode card = root.getChildren().get(0).getChildren().get(0);
        assertThat(card.getLayout().getDirection()).isEqualTo(Direction.COLUMN);
        assertThat(card.getSizing().getVertical()).isEqualTo(SizingMode.HUG);
        assertThat(card.getSizing().getHorizontal()).isEqualTo(SizingMode.FIXED);
    }

    @Test
    void keepsNonAutoContainerAbsoluteAndHandlesMissingProperties() throws Exception {
        DesignNode root = parser.parseDesignTree("""
                {"document":{"id":"0:0","type":"DOCUMENT","children":[
                  {"id":"1:0","type":"CANVAS","children":[
                    {"id":"2:0","name":"Freeform","type":"FRAME","children":[
                      {"id":"3:0","type":"TEXT","characters":"Hello"}
                    ]}
                  ]}
                ]}}
                """);

        DesignNode freeform = root.getChildren().get(0).getChildren().get(0);
        assertThat(freeform.getLayout().getType()).isEqualTo(LayoutType.ABSOLUTE);
        assertThat(freeform.getBounds().width()).isNull();
        assertThat(freeform.getChildren().get(0).getText()).isEqualTo("Hello");
    }
}
