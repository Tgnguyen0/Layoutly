package com.tgnguyen.layoutlybe.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgnguyen.layoutlybe.model.UINode;
import org.springframework.stereotype.Service;

@Service
public class FigmaParserService {
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Nhan chuoi JSON tho tu Figma (response cua /files/{fileKey}),
    // duyet vao field "document" va dung lai thanh cay UINode.
    public UINode parseDocumentTree(String rawFigmaJson) throws JsonProcessingException {
        JsonNode rootNode = objectMapper.readTree(rawFigmaJson);
        JsonNode documentNode = rootNode.get("document");

        if (documentNode == null) {
            throw new IllegalArgumentException(
                    "JSON khong co field 'document' - phai la response tu endpoint /files/{fileKey}");
        }

        return buildNode(documentNode);
    }

    private UINode buildNode(JsonNode node) {
        UINode uiNode = new UINode();
        uiNode.setId(textOf(node, "id"));
        uiNode.setName(textOf(node, "name"));
        uiNode.setType(textOf(node, "type"));
        uiNode.setCharacters(textOf(node, "characters"));

        // Vi tri + kich thuoc
        JsonNode box = node.get("absoluteBoundingBox");
        if (box != null) {
            uiNode.setX(doubleOf(box, "x"));
            uiNode.setY(doubleOf(box, "y"));
            uiNode.setWidth(doubleOf(box, "width"));
            uiNode.setHeight(doubleOf(box, "height"));
        }

        // Mau nen + vien
        uiNode.setBackgroundColor(extractColor(node.get("fills")));
        uiNode.setBorderColor(extractColor(node.get("strokes")));
        uiNode.setBorderWidth(doubleOf(node, "strokeWeight"));

        // Do trong, bo goc
        uiNode.setOpacity(doubleOf(node, "opacity"));
        uiNode.setCornerRadius(doubleOf(node, "cornerRadius"));

        // Khoang cach (Auto Layout) - chi co gia tri neu Frame co bat Auto Layout
        uiNode.setPaddingTop(doubleOf(node, "paddingTop"));
        uiNode.setPaddingRight(doubleOf(node, "paddingRight"));
        uiNode.setPaddingBottom(doubleOf(node, "paddingBottom"));
        uiNode.setPaddingLeft(doubleOf(node, "paddingLeft"));
        uiNode.setItemSpacing(doubleOf(node, "itemSpacing"));

        // Font chu - field "style" chi ton tai o node type TEXT
        JsonNode style = node.get("style");
        if (style != null) {
            uiNode.setFontFamily(textOf(style, "fontFamily"));
            uiNode.setFontSize(doubleOf(style, "fontSize"));
            uiNode.setFontWeight(doubleOf(style, "fontWeight"));
            uiNode.setLineHeight(doubleOf(style, "lineHeightPx"));
            uiNode.setLetterSpacing(doubleOf(style, "letterSpacing"));
        }

        JsonNode childrenNode = node.get("children");
        if (childrenNode != null && childrenNode.isArray()) {
            for (JsonNode child : childrenNode) {
                uiNode.getChildren().add(buildNode(child));
            }
        }
        return uiNode;
    }

    private String textOf(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null ? value.asText() : null;
    }

    private Double doubleOf(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value != null && !value.isNull()) ? value.asDouble() : null;
    }

    // Lay mau SOLID dau tien, con hien thi (visible != false), tra ve dang rgba() dung cho CSS
    private String extractColor(JsonNode fillsOrStrokes) {
        if (fillsOrStrokes == null || !fillsOrStrokes.isArray()) return null;

        for (JsonNode paint : fillsOrStrokes) {
            String paintType = textOf(paint, "type");
            JsonNode visibleNode = paint.get("visible");
            boolean visible = visibleNode == null || visibleNode.asBoolean(true);

            if ("SOLID".equals(paintType) && visible) {
                JsonNode color = paint.get("color");
                if (color == null) continue;

                double r = color.get("r").asDouble();
                double g = color.get("g").asDouble();
                double b = color.get("b").asDouble();
                // Do mo co the nam o paint.opacity hoac color.a, uu tien paint.opacity neu co
                double a = paint.has("opacity") ? paint.get("opacity").asDouble() : color.get("a").asDouble();

                int ri = (int) Math.round(r * 255);
                int gi = (int) Math.round(g * 255);
                int bi = (int) Math.round(b * 255);

                return String.format("rgba(%d, %d, %d, %.2f)", ri, gi, bi, a);
            }
        }
        return null;
    }
}
