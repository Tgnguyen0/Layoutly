package com.tgnguyen.layoutlybe.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgnguyen.layoutlybe.model.UINode;
import com.tgnguyen.layoutlybe.model.ir.Alignment;
import com.tgnguyen.layoutlybe.model.ir.AssetSpec;
import com.tgnguyen.layoutlybe.model.ir.Bounds;
import com.tgnguyen.layoutlybe.model.ir.ConstraintSpec;
import com.tgnguyen.layoutlybe.model.ir.DesignNode;
import com.tgnguyen.layoutlybe.model.ir.DesignNodeMapper;
import com.tgnguyen.layoutlybe.model.ir.Direction;
import com.tgnguyen.layoutlybe.model.ir.EdgeInsets;
import com.tgnguyen.layoutlybe.model.ir.LayoutSpec;
import com.tgnguyen.layoutlybe.model.ir.LayoutType;
import com.tgnguyen.layoutlybe.model.ir.Positioning;
import com.tgnguyen.layoutlybe.model.ir.SizingMode;
import com.tgnguyen.layoutlybe.model.ir.SizingSpec;
import com.tgnguyen.layoutlybe.model.ir.StyleSpec;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class FigmaParserService {
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Legacy entry point kept for existing clients of /tree and older generators.
     */
    public UINode parseDocumentTree(String rawFigmaJson) throws JsonProcessingException {
        return DesignNodeMapper.toLegacy(parseDesignTree(rawFigmaJson));
    }

    /**
     * Parses the Figma document into Layoutly's framework-neutral intermediate representation.
     */
    public DesignNode parseDesignTree(String rawFigmaJson) throws JsonProcessingException {
        JsonNode rootNode = objectMapper.readTree(rawFigmaJson);
        JsonNode documentNode = rootNode.get("document");
        if (documentNode == null || documentNode.isNull()) {
            throw new IllegalArgumentException(
                    "JSON khong co field 'document' - phai la response tu endpoint /files/{fileKey}");
        }
        return buildNode(documentNode, Direction.NONE);
    }

    private DesignNode buildNode(JsonNode node, Direction parentDirection) {
        LayoutSpec layout = parseLayout(node);
        Bounds bounds = parseBounds(node);
        List<DesignNode> children = new ArrayList<>();
        JsonNode childrenNode = node.get("children");
        if (childrenNode != null && childrenNode.isArray()) {
            for (JsonNode child : childrenNode) {
                children.add(buildNode(child, layout.getDirection()));
            }
        }

        return DesignNode.builder()
                .id(textOf(node, "id"))
                .name(textOf(node, "name"))
                .type(textOf(node, "type"))
                .text(textOf(node, "characters"))
                .bounds(bounds)
                .layout(layout)
                .sizing(parseSizing(node, bounds, parentDirection))
                .style(parseStyle(node))
                .asset(parseAsset(node))
                .children(children)
                .build();
    }

    private LayoutSpec parseLayout(JsonNode node) {
        String layoutMode = textOf(node, "layoutMode");
        Direction direction = switch (valueOrEmpty(layoutMode)) {
            case "HORIZONTAL" -> Direction.ROW;
            case "VERTICAL" -> Direction.COLUMN;
            default -> Direction.NONE;
        };

        LayoutType layoutType;
        if (direction != Direction.NONE) {
            layoutType = LayoutType.AUTO_FLEX;
        } else if ("GRID".equals(layoutMode)) {
            layoutType = LayoutType.AUTO_GRID;
        } else if (hasChildren(node) && isRenderableContainer(textOf(node, "type"))) {
            layoutType = LayoutType.ABSOLUTE;
        } else {
            layoutType = LayoutType.NONE;
        }

        JsonNode constraints = node.get("constraints");
        return LayoutSpec.builder()
                .type(layoutType)
                .direction(direction)
                .gap(doubleOf(node, "itemSpacing"))
                .padding(new EdgeInsets(
                        doubleOf(node, "paddingTop"),
                        doubleOf(node, "paddingRight"),
                        doubleOf(node, "paddingBottom"),
                        doubleOf(node, "paddingLeft")))
                .mainAxisAlignment(parseAlignment(textOf(node, "primaryAxisAlignItems")))
                .crossAxisAlignment(parseAlignment(textOf(node, "counterAxisAlignItems")))
                .wrap("WRAP".equals(textOf(node, "layoutWrap")))
                .positioning(parsePositioning(textOf(node, "layoutPositioning")))
                .constraints(new ConstraintSpec(
                        textOf(constraints, "horizontal"),
                        textOf(constraints, "vertical")))
                .build();
    }

    private SizingSpec parseSizing(JsonNode node, Bounds bounds, Direction parentDirection) {
        SizingMode horizontal = parseSizingMode(textOf(node, "layoutSizingHorizontal"));
        SizingMode vertical = parseSizingMode(textOf(node, "layoutSizingVertical"));

        Direction ownDirection = parseDirection(textOf(node, "layoutMode"));
        String primaryMode = textOf(node, "primaryAxisSizingMode");
        String counterMode = textOf(node, "counterAxisSizingMode");
        if (ownDirection == Direction.ROW) {
            horizontal = fallbackSizing(horizontal, primaryMode);
            vertical = fallbackSizing(vertical, counterMode);
        } else if (ownDirection == Direction.COLUMN) {
            vertical = fallbackSizing(vertical, primaryMode);
            horizontal = fallbackSizing(horizontal, counterMode);
        }

        if (doubleOf(node, "layoutGrow") != null && doubleOf(node, "layoutGrow") > 0) {
            if (parentDirection == Direction.ROW) horizontal = SizingMode.FILL;
            if (parentDirection == Direction.COLUMN) vertical = SizingMode.FILL;
        }
        if ("STRETCH".equals(textOf(node, "layoutAlign"))) {
            if (parentDirection == Direction.ROW) vertical = SizingMode.STRETCH;
            if (parentDirection == Direction.COLUMN) horizontal = SizingMode.STRETCH;
        }

        if (horizontal == SizingMode.UNKNOWN && bounds.width() != null) horizontal = SizingMode.FIXED;
        if (vertical == SizingMode.UNKNOWN && bounds.height() != null) vertical = SizingMode.FIXED;

        return SizingSpec.builder()
                .horizontal(horizontal)
                .vertical(vertical)
                .width(bounds.width())
                .height(bounds.height())
                .minWidth(doubleOf(node, "minWidth"))
                .maxWidth(doubleOf(node, "maxWidth"))
                .minHeight(doubleOf(node, "minHeight"))
                .maxHeight(doubleOf(node, "maxHeight"))
                .build();
    }

    private StyleSpec parseStyle(JsonNode node) {
        JsonNode textStyle = node.get("style");
        return StyleSpec.builder()
                .backgroundColor(extractColor(node.get("fills")))
                .borderColor(extractColor(node.get("strokes")))
                .borderWidth(doubleOf(node, "strokeWeight"))
                .opacity(doubleOf(node, "opacity"))
                .cornerRadius(doubleOf(node, "cornerRadius"))
                .fontFamily(textOf(textStyle, "fontFamily"))
                .fontSize(doubleOf(textStyle, "fontSize"))
                .fontWeight(doubleOf(textStyle, "fontWeight"))
                .lineHeight(doubleOf(textStyle, "lineHeightPx"))
                .letterSpacing(doubleOf(textStyle, "letterSpacing"))
                .build();
    }

    private AssetSpec parseAsset(JsonNode node) {
        String type = textOf(node, "type");
        if (isVectorType(type)) return new AssetSpec(true, "VECTOR");

        JsonNode fills = node.get("fills");
        if (fills != null && fills.isArray()) {
            for (JsonNode paint : fills) {
                if (isVisible(paint) && "IMAGE".equals(textOf(paint, "type"))) {
                    return new AssetSpec(true, "IMAGE");
                }
            }
        }
        return AssetSpec.none();
    }

    private Bounds parseBounds(JsonNode node) {
        JsonNode box = node.get("absoluteBoundingBox");
        if (box == null || box.isNull()) return Bounds.empty();
        return new Bounds(
                doubleOf(box, "x"),
                doubleOf(box, "y"),
                doubleOf(box, "width"),
                doubleOf(box, "height"));
    }

    private Alignment parseAlignment(String value) {
        return switch (valueOrEmpty(value)) {
            case "MIN" -> Alignment.START;
            case "CENTER" -> Alignment.CENTER;
            case "MAX" -> Alignment.END;
            case "SPACE_BETWEEN" -> Alignment.SPACE_BETWEEN;
            case "SPACE_AROUND" -> Alignment.SPACE_AROUND;
            case "BASELINE" -> Alignment.BASELINE;
            case "STRETCH" -> Alignment.STRETCH;
            default -> Alignment.START;
        };
    }

    private Direction parseDirection(String value) {
        return switch (valueOrEmpty(value)) {
            case "HORIZONTAL" -> Direction.ROW;
            case "VERTICAL" -> Direction.COLUMN;
            default -> Direction.NONE;
        };
    }

    private Positioning parsePositioning(String value) {
        if (value == null || value.isBlank()) return Positioning.AUTO;
        return "ABSOLUTE".equals(value) ? Positioning.ABSOLUTE : Positioning.AUTO;
    }

    private SizingMode parseSizingMode(String value) {
        return switch (valueOrEmpty(value)) {
            case "FIXED" -> SizingMode.FIXED;
            case "HUG" -> SizingMode.HUG;
            case "FILL" -> SizingMode.FILL;
            default -> SizingMode.UNKNOWN;
        };
    }

    private SizingMode fallbackSizing(SizingMode current, String legacyMode) {
        if (current != SizingMode.UNKNOWN) return current;
        return switch (valueOrEmpty(legacyMode)) {
            case "FIXED" -> SizingMode.FIXED;
            case "AUTO" -> SizingMode.HUG;
            default -> SizingMode.UNKNOWN;
        };
    }

    private boolean hasChildren(JsonNode node) {
        JsonNode children = node.get("children");
        return children != null && children.isArray() && !children.isEmpty();
    }

    private boolean isRenderableContainer(String type) {
        return !"DOCUMENT".equals(type) && !"CANVAS".equals(type);
    }

    private boolean isVectorType(String type) {
        return "VECTOR".equals(type)
                || "BOOLEAN_OPERATION".equals(type)
                || "STAR".equals(type)
                || "LINE".equals(type)
                || "REGULAR_POLYGON".equals(type)
                || "POLYGON".equals(type);
    }

    private boolean isVisible(JsonNode paint) {
        JsonNode visible = paint.get("visible");
        return visible == null || visible.asBoolean(true);
    }

    private String textOf(JsonNode node, String field) {
        if (node == null || node.isNull()) return null;
        JsonNode value = node.get(field);
        return value != null && !value.isNull() ? value.asText() : null;
    }

    private Double doubleOf(JsonNode node, String field) {
        if (node == null || node.isNull()) return null;
        JsonNode value = node.get(field);
        return value != null && !value.isNull() && value.isNumber() ? value.asDouble() : null;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String extractColor(JsonNode paints) {
        if (paints == null || !paints.isArray()) return null;
        for (JsonNode paint : paints) {
            if (!"SOLID".equals(textOf(paint, "type")) || !isVisible(paint)) continue;
            JsonNode color = paint.get("color");
            if (color == null || !color.has("r") || !color.has("g") || !color.has("b")) continue;

            int red = toColorChannel(color.get("r").asDouble());
            int green = toColorChannel(color.get("g").asDouble());
            int blue = toColorChannel(color.get("b").asDouble());
            double alpha = paint.hasNonNull("opacity")
                    ? paint.get("opacity").asDouble()
                    : color.path("a").asDouble(1.0);
            return String.format(java.util.Locale.ROOT, "rgba(%d, %d, %d, %.2f)", red, green, blue, alpha);
        }
        return null;
    }

    private int toColorChannel(double value) {
        return (int) Math.round(Math.max(0, Math.min(1, value)) * 255);
    }
}
