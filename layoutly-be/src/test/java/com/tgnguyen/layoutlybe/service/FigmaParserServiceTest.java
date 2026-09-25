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
