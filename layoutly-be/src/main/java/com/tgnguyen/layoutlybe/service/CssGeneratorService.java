package com.tgnguyen.layoutlybe.service;

import com.tgnguyen.layoutlybe.model.UINode;
import com.tgnguyen.layoutlybe.model.ir.Alignment;
import com.tgnguyen.layoutlybe.model.ir.Bounds;
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

import java.util.List;
import java.util.Map;

@Service
public class CssGeneratorService {

    public String generate(UINode root) {
        return generate(DesignNodeMapper.fromLegacy(root), Map.of());
    }

    public String generate(UINode root, Map<String, String> imageUrlByNodeId) {
        return generate(DesignNodeMapper.fromLegacy(root), imageUrlByNodeId);
    }

    public String generate(DesignNode root) {
        return generate(root, Map.of());
    }

    public String generate(DesignNode root, Map<String, String> imageUrlByNodeId) {
        StringBuilder css = new StringBuilder();
        appendBaseStyles(css);
        if (root == null) return css.toString();

        for (DesignNode child : childrenOf(root)) {
            walk(child, root, css, imageUrlByNodeId);
        }
        return css.toString();
    }

    public String generateForNode(UINode node, Map<String, String> imageUrlByNodeId) {
        return generateForNode(DesignNodeMapper.fromLegacy(node), imageUrlByNodeId);
    }

    public String generateForNode(DesignNode node, Map<String, String> imageUrlByNodeId) {
        StringBuilder css = new StringBuilder();
        appendBaseStyles(css);
        if (node == null) return css.toString();

        css.append(".node-").append(toClassName(node.getId())).append(" {\n");
        css.append("  position: relative;\n");
        writeLayoutContainer(node, css);
        writeCommonProperties(node, null, css, imageUrlByNodeId);
        css.append("}\n\n");

        for (DesignNode child : childrenOf(node)) {
            walk(child, node, css, imageUrlByNodeId);
        }
        return css.toString();
    }

    private void walk(DesignNode node, DesignNode parent, StringBuilder css,
                      Map<String, String> imageUrlByNodeId) {
        if ("CANVAS".equals(node.getType())) {
            for (DesignNode child : childrenOf(node)) {
                walk(child, parent, css, imageUrlByNodeId);
            }
            return;
        }

        css.append(".node-").append(toClassName(node.getId())).append(" {\n");
        writePosition(node, parent, css);
        writeLayoutContainer(node, css);
        writeCommonProperties(node, parent, css, imageUrlByNodeId);
        css.append("}\n\n");

        for (DesignNode child : childrenOf(node)) {
            walk(child, node, css, imageUrlByNodeId);
        }
    }

    private void writePosition(DesignNode node, DesignNode parent, StringBuilder css) {
        boolean parentIsFlex = isAutoFlex(parent);
        boolean forcedAbsolute = layoutOf(node).getPositioning() == Positioning.ABSOLUTE;
        if (parentIsFlex && !forcedAbsolute) {
            css.append("  position: relative;\n");
            return;
        }

        Bounds bounds = boundsOf(node);
        Bounds parentBounds = boundsOf(parent);
        if (bounds.x() != null && bounds.y() != null && parentBounds.x() != null && parentBounds.y() != null) {
            css.append("  position: absolute;\n");
            css.append("  left: ").append(round(bounds.x() - parentBounds.x())).append("px;\n");
            css.append("  top: ").append(round(bounds.y() - parentBounds.y())).append("px;\n");
        } else if (bounds.x() != null && bounds.y() != null) {
            css.append("  position: absolute;\n");
            css.append("  left: calc(").append(round(bounds.x()))
                    .append("px - (var(--figma-offset-x) * 1px));\n");
            css.append("  top: calc(").append(round(bounds.y()))
                    .append("px - (var(--figma-offset-y) * 1px));\n");
        } else {
            css.append("  position: relative;\n");
        }
    }

    private void writeLayoutContainer(DesignNode node, StringBuilder css) {
        LayoutSpec layout = layoutOf(node);
        if (layout.getType() != LayoutType.AUTO_FLEX) return;

        css.append("  display: flex;\n");
        css.append("  flex-direction: ")
                .append(layout.getDirection() == Direction.COLUMN ? "column" : "row")
                .append(";\n");
        if (layout.getGap() != null) css.append("  gap: ").append(round(layout.getGap())).append("px;\n");
        writePadding(layout.getPadding(), css);
        css.append("  justify-content: ").append(toJustifyContent(layout.getMainAxisAlignment())).append(";\n");
        css.append("  align-items: ").append(toAlignItems(layout.getCrossAxisAlignment())).append(";\n");
        if (layout.isWrap()) css.append("  flex-wrap: wrap;\n");
    }

    private void writePadding(EdgeInsets padding, StringBuilder css) {
        if (padding == null) return;
        Double top = padding.top();
        Double right = padding.right();
        Double bottom = padding.bottom();
        Double left = padding.left();
        if (top == null && right == null && bottom == null && left == null) return;

        css.append("  padding: ")
                .append(round(orZero(top))).append("px ")
                .append(round(orZero(right))).append("px ")
                .append(round(orZero(bottom))).append("px ")
                .append(round(orZero(left))).append("px;\n");
    }

    private void writeCommonProperties(DesignNode node, DesignNode parent, StringBuilder css,
                                       Map<String, String> imageUrlByNodeId) {
        writeSizing(node, parent, css);
        StyleSpec style = styleOf(node);

        String imageUrl = imageUrlByNodeId.get(node.getId());
        if (imageUrl != null && !imageUrl.isBlank()) {
            css.append("  background-image: url(\"").append(escapeCssUrl(imageUrl)).append("\");\n");
            css.append("  background-size: cover;\n");
            css.append("  background-position: center;\n");
            css.append("  background-repeat: no-repeat;\n");
        } else if (style.getBackgroundColor() != null) {
            if ("TEXT".equals(node.getType())) {
                css.append("  color: ").append(style.getBackgroundColor()).append(";\n");
            } else {
                css.append("  background-color: ").append(style.getBackgroundColor()).append(";\n");
            }
        }
        if (style.getOpacity() != null && style.getOpacity() < 1.0) {
            css.append("  opacity: ").append(style.getOpacity()).append(";\n");
        }
        if (style.getCornerRadius() != null && style.getCornerRadius() > 0) {
            css.append("  border-radius: ").append(round(style.getCornerRadius())).append("px;\n");
        }
        if (style.getBorderColor() != null && style.getBorderWidth() != null) {
            css.append("  border: ").append(round(style.getBorderWidth())).append("px solid ")
                    .append(style.getBorderColor()).append(";\n");
        }

        if ("TEXT".equals(node.getType())) {
            css.append("  white-space: pre-wrap;\n");
            if (style.getFontFamily() != null) {
                css.append("  font-family: '").append(escapeCssString(style.getFontFamily()))
                        .append("', sans-serif;\n");
            }
            if (style.getFontSize() != null) css.append("  font-size: ").append(round(style.getFontSize())).append("px;\n");
            if (style.getFontWeight() != null) css.append("  font-weight: ").append(style.getFontWeight().intValue()).append(";\n");
            if (style.getLineHeight() != null) css.append("  line-height: ").append(round(style.getLineHeight())).append("px;\n");
            if (style.getLetterSpacing() != null) css.append("  letter-spacing: ").append(round(style.getLetterSpacing())).append("px;\n");
        }
    }

    private void writeSizing(DesignNode node, DesignNode parent, StringBuilder css) {
        SizingSpec sizing = sizingOf(node);
        boolean inFlex = isAutoFlex(parent) && layoutOf(node).getPositioning() != Positioning.ABSOLUTE;

        writeDimension("width", sizing.getHorizontal(), sizing.getWidth(), css);
        writeDimension("height", sizing.getVertical(), sizing.getHeight(), css);
        writeOptionalDimension("min-width", sizing.getMinWidth(), css);
        writeOptionalDimension("max-width", sizing.getMaxWidth(), css);
        writeOptionalDimension("min-height", sizing.getMinHeight(), css);
        writeOptionalDimension("max-height", sizing.getMaxHeight(), css);

        if (inFlex) {
            Direction parentDirection = layoutOf(parent).getDirection();
            boolean fillsMainAxis = parentDirection == Direction.ROW
                    ? sizing.getHorizontal() == SizingMode.FILL
                    : sizing.getVertical() == SizingMode.FILL;
            if (fillsMainAxis) css.append("  flex: 1 1 0;\n");

            boolean stretchesCrossAxis = parentDirection == Direction.ROW
                    ? sizing.getVertical() == SizingMode.STRETCH
                    : sizing.getHorizontal() == SizingMode.STRETCH;
            if (stretchesCrossAxis) css.append("  align-self: stretch;\n");
        }
    }

    private void writeDimension(String property, SizingMode mode, Double fixedValue, StringBuilder css) {
        if (mode == SizingMode.HUG) {
            css.append("  ").append(property).append(": fit-content;\n");
        } else if (mode == SizingMode.FILL || mode == SizingMode.STRETCH) {
            css.append("  ").append(property).append(": 100%;\n");
        } else if (fixedValue != null) {
            css.append("  ").append(property).append(": ").append(round(fixedValue)).append("px;\n");
        }
    }

    private void writeOptionalDimension(String property, Double value, StringBuilder css) {
        if (value != null) css.append("  ").append(property).append(": ").append(round(value)).append("px;\n");
    }

    private void appendBaseStyles(StringBuilder css) {
        css.append("* { box-sizing: border-box; margin: 0; padding: 0; }\n\n");
        css.append("html, body { min-height: 100%; }\n");
        css.append("body {\n  font-family: Arial, sans-serif;\n  background: #1e1e1e;\n  overflow-x: hidden;\n}\n\n");
        css.append(".figma-page {\n");
        css.append("  width: 100vw;\n");
        css.append("  height: calc(var(--figma-height) * 1px * (100vw / (var(--figma-width) * 1px)));\n");
        css.append("  margin: 0 auto;\n  background: #ffffff;\n  overflow: hidden;\n}\n\n");
        css.append(".figma-canvas {\n");
        css.append("  position: relative;\n");
        css.append("  width: calc(var(--figma-width) * 1px);\n");
        css.append("  height: calc(var(--figma-height) * 1px);\n");
        css.append("  transform: scale(calc(100vw / (var(--figma-width) * 1px)));\n");
        css.append("  transform-origin: top left;\n}\n\n");
        css.append(".figma-node { overflow: hidden; }\n\n");
    }

    private boolean isAutoFlex(DesignNode node) {
        return node != null && layoutOf(node).getType() == LayoutType.AUTO_FLEX;
    }

    private LayoutSpec layoutOf(DesignNode node) {
        return node != null && node.getLayout() != null ? node.getLayout() : LayoutSpec.builder().build();
    }

    private SizingSpec sizingOf(DesignNode node) {
        return node != null && node.getSizing() != null ? node.getSizing() : SizingSpec.builder().build();
    }

    private StyleSpec styleOf(DesignNode node) {
        return node != null && node.getStyle() != null ? node.getStyle() : StyleSpec.builder().build();
    }

    private Bounds boundsOf(DesignNode node) {
        return node != null && node.getBounds() != null ? node.getBounds() : Bounds.empty();
    }

    private List<DesignNode> childrenOf(DesignNode node) {
        return node != null && node.getChildren() != null ? node.getChildren() : List.of();
    }

    private String toJustifyContent(Alignment alignment) {
        return switch (alignment == null ? Alignment.START : alignment) {
            case CENTER -> "center";
            case END -> "flex-end";
            case SPACE_BETWEEN -> "space-between";
            case SPACE_AROUND -> "space-around";
            default -> "flex-start";
        };
    }

    private String toAlignItems(Alignment alignment) {
        return switch (alignment == null ? Alignment.START : alignment) {
            case CENTER -> "center";
            case END -> "flex-end";
            case BASELINE -> "baseline";
            case STRETCH -> "stretch";
            default -> "flex-start";
        };
    }

    private double orZero(Double value) {
        return value != null ? value : 0;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String toClassName(String name) {
        if (name == null || name.isBlank()) return "node";
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "node" : slug;
    }

    private String escapeCssUrl(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String escapeCssString(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }
}
