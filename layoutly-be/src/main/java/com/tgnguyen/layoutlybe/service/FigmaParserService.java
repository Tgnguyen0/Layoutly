package com.tgnguyen.layoutlybe.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgnguyen.layoutlybe.model.UINode;
import com.tgnguyen.layoutlybe.model.ir.*;
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

        LinkInfo link = parseLink(node);
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
                .role(RoleClassifier.classify(node))   // <-- thêm dòng này
                .linkTargetId(link.destinationId())
                .transition(link.transition())
                .linkNavigation(link.navigation())
                .children(children)
                .build();
    }

    private record LinkInfo(String destinationId, String navigation, TransitionSpec transition) {
        static LinkInfo none() {
            return new LinkInfo(null, null, TransitionSpec.none());
        }
    }

    private LinkInfo parseLink(JsonNode node) {
        JsonNode interactions = node.get("interactions");
        if (interactions == null || !interactions.isArray()) return LinkInfo.none();

        for (JsonNode interaction : interactions) {
            if (!"ON_CLICK".equals(textOf(interaction.get("trigger"), "type"))) continue;

            JsonNode actions = interaction.get("actions");
            if (actions == null || !actions.isArray()) continue;

            for (JsonNode action : actions) {
                String navigation = textOf(action, "navigation");
                if (!"NODE".equals(textOf(action, "type"))
                        || !("NAVIGATE".equals(navigation) || "SCROLL_TO".equals(navigation))) continue;

                String destinationId = textOf(action, "destinationId");
                if (destinationId == null || destinationId.isBlank()) continue;

                return new LinkInfo(destinationId, navigation, parseTransition(action.get("transition"), node));
            }
        }
        return LinkInfo.none();
    }

    private TransitionSpec parseTransition(JsonNode transition, JsonNode node) {
        String type = null;
        Integer durationMs = null;
        String easing = null;

        if (transition != null && !transition.isNull()) {
            type = textOf(transition, "type");
            Double seconds = doubleOf(transition, "duration");      // trong action: don vi giay
            if (seconds != null) durationMs = (int) Math.round(seconds * 1000);
            easing = parseEasing(transition.get("easing"));
        }

        // Fallback: field cu tren node (don vi ms), day la nguon chinh cho link ON_CLICK
        if (durationMs == null) {
            Double ms = doubleOf(node, "transitionDuration");
            if (ms != null) durationMs = (int) Math.round(ms);
        }
        if (easing == null) easing = textOf(node, "transitionEasing");

        return new TransitionSpec(type, durationMs, easing);
    }

    private String parseEasing(JsonNode easing) {
        if (easing == null || easing.isNull()) return null;
        String type = textOf(easing, "type");
        if ("CUSTOM_CUBIC_BEZIER".equals(type)) {
            JsonNode b = easing.get("easingFunctionCubicBezier");
            Double x1 = doubleOf(b, "x1"), y1 = doubleOf(b, "y1");
            Double x2 = doubleOf(b, "x2"), y2 = doubleOf(b, "y2");
            if (x1 != null && y1 != null && x2 != null && y2 != null) {
                return String.format(java.util.Locale.ROOT, "cubic-bezier(%s, %s, %s, %s)", x1, y1, x2, y2);
            }
        }
        return type;
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
                .backgroundGradient(extractLinearGradient(node))
                .borderColor(extractColor(node.get("strokes")))
                .borderWidth(doubleOf(node, "strokeWeight"))
                .opacity(doubleOf(node, "opacity"))
                .cornerRadius(doubleOf(node, "cornerRadius"))
                .fontFamily(textOf(textStyle, "fontFamily"))
                .fontSize(doubleOf(textStyle, "fontSize"))
                .fontWeight(doubleOf(textStyle, "fontWeight"))
                .lineHeight(doubleOf(textStyle, "lineHeightPx"))
                .letterSpacing(doubleOf(textStyle, "letterSpacing"))
                .textAlign(textOf(textStyle, "textAlignHorizontal"))
                .textAutoResize(textOf(textStyle, "textAutoResize"))
                .fontStyle(textOf(textStyle, "fontStyle"))
                .textCase(textOf(textStyle, "textCase"))
                .cornerRadii(parseCornerRadii(node))
                .shadows(parseShadows(node))
                .clipsContent(booleanOf(node, "clipsContent"))
                .visible(booleanOf(node, "visible"))
                .build();
    }

    private List<Double> parseCornerRadii(JsonNode node) {
        JsonNode radii = node.get("rectangleCornerRadii");
        if (radii == null || !radii.isArray() || radii.size() != 4) return List.of();
        List<Double> result = new ArrayList<>();
        for (JsonNode radius : radii) {
            if (!radius.isNumber()) return List.of();
            result.add(Math.max(0, radius.asDouble()));
        }
        Double rotation = doubleOf(node, "rotation");
        if (rotation != null) {
            int turns = (int) Math.round(rotation / (Math.PI / 2));
            // REST bounds already include rotation; rotate the corners, not the bounds again.
            if (Math.abs(rotation - turns * Math.PI / 2) < 0.0001) {
                java.util.Collections.rotate(result, turns);
            }
        }
        return result;
    }

    private List<ShadowSpec> parseShadows(JsonNode node) {
        List<ShadowSpec> shadows = new ArrayList<>();
        JsonNode effects = node.get("effects");
        if (effects == null || !effects.isArray()) return shadows;
        for (JsonNode effect : effects) {
            String type = textOf(effect, "type");
            if (!isVisible(effect) || !("DROP_SHADOW".equals(type) || "INNER_SHADOW".equals(type))) continue;
            String color = colorOf(effect.get("color"), null);
            if (color == null) continue;
            JsonNode offset = effect.path("offset");
            shadows.add(new ShadowSpec("INNER_SHADOW".equals(type),
                    offset.path("x").asDouble(), offset.path("y").asDouble(),
                    effect.path("radius").asDouble(), effect.path("spread").asDouble(), color));
        }
        return shadows;
    }

    private AssetSpec parseAsset(JsonNode node) {
        String type = textOf(node, "type");
        if (isVectorType(type)) return new AssetSpec(true, "VECTOR", parseBounds(node, "absoluteRenderBounds"));

        JsonNode fills = node.get("fills");
        if (fills != null && fills.isArray()) {
            for (JsonNode paint : fills) {
                if (isVisible(paint) && "IMAGE".equals(textOf(paint, "type"))) {
                    return new AssetSpec(true, "IMAGE", parseBounds(node, "absoluteRenderBounds"));
                }
            }
        }
        return AssetSpec.none();
    }

    private Bounds parseBounds(JsonNode node) {
        return parseBounds(node, "absoluteBoundingBox");
    }

    private Bounds parseBounds(JsonNode node, String property) {
        JsonNode box = node.get(property);
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

    private Boolean booleanOf(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isBoolean() ? value.asBoolean() : null;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String extractColor(JsonNode paints) {
        if (paints == null || !paints.isArray()) return null;
        for (JsonNode paint : paints) {
            if (!"SOLID".equals(textOf(paint, "type")) || !isVisible(paint)) continue;
            JsonNode color = paint.get("color");
            String cssColor = colorOf(color, doubleOf(paint, "opacity"));
            if (cssColor != null) return cssColor;
        }
        return null;
    }

    private String extractLinearGradient(JsonNode node) {
        JsonNode fills = node.get("fills");
        if (fills == null || !fills.isArray()) return null;
        for (JsonNode paint : fills) {
            if (!isVisible(paint) || !"GRADIENT_LINEAR".equals(textOf(paint, "type"))) continue;
            JsonNode handles = paint.path("gradientHandlePositions"), stops = paint.path("gradientStops");
            if (!handles.isArray() || handles.size() < 2 || !stops.isArray() || stops.size() < 2) continue;
            double width = node.path("absoluteBoundingBox").path("width").asDouble(1);
            double height = node.path("absoluteBoundingBox").path("height").asDouble(1);
            double startX = handles.get(0).path("x").asDouble() * width;
            double startY = handles.get(0).path("y").asDouble() * height;
            double dx = handles.get(1).path("x").asDouble() * width - startX;
            double dy = handles.get(1).path("y").asDouble() * height - startY;
            double length = Math.hypot(dx, dy);
            if (length < .000001) continue;
            double ux = dx / length, uy = dy / length;
            double cssLength = Math.abs(width * ux) + Math.abs(height * uy);
            if (cssLength < .000001) continue;
            double offset = (startX - width / 2) * ux + (startY - height / 2) * uy + cssLength / 2;
            double angle = (Math.toDegrees(Math.atan2(ux, -uy)) + 360) % 360;
            List<String> colors = new ArrayList<>();
            for (JsonNode stop : stops) {
                String color = colorOf(stop.get("color"), doubleOf(paint, "opacity"));
                if (color != null) colors.add(String.format(java.util.Locale.ROOT, "%s %.4f%%", color,
                        (offset + stop.path("position").asDouble() * length) / cssLength * 100));
            }
            if (colors.size() >= 2) return String.format(java.util.Locale.ROOT, "linear-gradient(%.4fdeg, %s)", angle, String.join(", ", colors));
        }
        return null;
    }

    private String colorOf(JsonNode color, Double opacity) {
        if (color == null || !color.has("r") || !color.has("g") || !color.has("b")) return null;
        double alpha = color.path("a").asDouble(1.0) * (opacity == null ? 1.0 : opacity);
        return String.format(java.util.Locale.ROOT, "rgba(%d, %d, %d, %.2f)",
                toColorChannel(color.get("r").asDouble()), toColorChannel(color.get("g").asDouble()),
                toColorChannel(color.get("b").asDouble()), alpha);
    }

    private int toColorChannel(double value) {
        return (int) Math.round(Math.max(0, Math.min(1, value)) * 255);
    }
}
