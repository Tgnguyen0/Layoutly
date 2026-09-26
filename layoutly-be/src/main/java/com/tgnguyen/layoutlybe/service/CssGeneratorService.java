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

    // Nguong de coi 1 node "khong set Constraints ro rang" la khoi full-bleed
    // (anh nen, dai mau nen...) va tu dong cho co gian full-width thay vi ket px.
    private static final double FULL_BLEED_RATIO_THRESHOLD = 0.95;

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
        writeCommonProperties(node, null, css, imageUrlByNodeId, false);
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
        boolean sizeHandledByPosition = writePosition(node, parent, css);
        writeLayoutContainer(node, css);
        writeCommonProperties(node, parent, css, imageUrlByNodeId, sizeHandledByPosition);
        css.append("}\n\n");

        for (DesignNode child : childrenOf(node)) {
            walk(child, node, css, imageUrlByNodeId);
        }
    }

    /**
     * @return true neu width/height cua node da duoc quyet dinh o day (writeSizing se bo qua,
     *         khong ghi de lai bang gia tri px co dinh tu Figma).
     */
    private boolean writePosition(DesignNode node, DesignNode parent, StringBuilder css) {
        boolean parentIsFlex = isAutoFlex(parent);
        boolean forcedAbsolute = layoutOf(node).getPositioning() == Positioning.ABSOLUTE;
        if (parentIsFlex && !forcedAbsolute) {
            css.append("  position: relative;\n");
            return false;
        }

        Bounds bounds = boundsOf(node);
        if (bounds.x() == null || bounds.y() == null) {
            css.append("  position: relative;\n");
            return false;
        }

        Bounds parentBounds = boundsOf(parent);
        if (parentBounds.x() == null || parentBounds.width() == null) {
            // Cha that (CANVAS/DOCUMENT) khong co bounds - Figma khong cho set Constraints
            // o tang nay. Day KHONG phai truong hop can "doan" gia tri cha: dung ban chat
            // la 1 section cua trang dai, nen xuat document flow binh thuong (khong absolute)
            // de trinh duyet tu xep chong theo dung thu tu HTML, chieu ngang fluid 100%.
            css.append("  position: relative;\n");
            css.append("  width: 100%;\n");
            if (bounds.height() != null) {
                css.append("  height: ").append(round(bounds.height())).append("px;\n");
            }
            return true;
        }

        css.append("  position: absolute;\n");
        writeConstrainedAxis(true, node, bounds, parentBounds, css);
        writeConstrainedAxis(false, node, bounds, parentBounds, css);
        return true;
    }

    private void writeConstrainedAxis(boolean horizontal, DesignNode node, Bounds bounds,
                                      Bounds parentBounds, StringBuilder css) {
        double nodeStart = horizontal ? bounds.x() : bounds.y();
        double nodeSize = (horizontal ? bounds.width() : bounds.height()) != null
                ? (horizontal ? bounds.width() : bounds.height()) : 0;
        double parentStart = horizontal ? parentBounds.x() : parentBounds.y();
        double parentSize = horizontal ? parentBounds.width() : parentBounds.height();

        String constraint = horizontal
                ? layoutOf(node).getConstraints().horizontal()
                : layoutOf(node).getConstraints().vertical();
        String leading = horizontal ? "left" : "top";
        String trailing = horizontal ? "right" : "bottom";
        String sizeProp = horizontal ? "width" : "height";

        double leadOffset = nodeStart - parentStart;
        double trailOffset = (parentStart + parentSize) - (nodeStart + nodeSize);

        switch (constraint == null ? "LEFT" : constraint) {
            case "RIGHT", "BOTTOM" -> {
                css.append("  ").append(trailing).append(": ").append(round(trailOffset)).append("px;\n");
                css.append("  ").append(sizeProp).append(": ").append(round(nodeSize)).append("px;\n");
            }
            case "CENTER" -> {
                double centerOffset = leadOffset + nodeSize / 2 - parentSize / 2;
                css.append("  ").append(leading).append(": calc(50% + ").append(round(centerOffset))
                        .append("px - ").append(round(nodeSize / 2)).append("px);\n");
                css.append("  ").append(sizeProp).append(": ").append(round(nodeSize)).append("px;\n");
            }
            case "LEFT_RIGHT", "TOP_BOTTOM" -> {
                css.append("  ").append(leading).append(": ").append(round(leadOffset)).append("px;\n");
                css.append("  ").append(trailing).append(": ").append(round(trailOffset)).append("px;\n");
                // Khong xuat width/height - de 2 canh tu keo gian
            }
            case "SCALE" -> {
                css.append("  ").append(leading).append(": ").append(round(leadOffset / parentSize * 100)).append("%;\n");
                css.append("  ").append(sizeProp).append(": ").append(round(nodeSize / parentSize * 100)).append("%;\n");
            }
            default -> { // LEFT / TOP - Figma khong bat buoc nguoi dung phai set constraint,
                // nen day la truong hop pho bien nhat trong thuc te. Rieng truc ngang: neu
                // node chiem gan het be rong cha (>= 95%), gan chac day la khoi full-bleed
                // (anh nen, dai mau...) du Figma chua khai bao LEFT_RIGHT - cho fluid luon
                // thay vi ket px, tranh phai bat nguoi dung vao Figma chinh tay tung node.
                boolean looksFullBleed = horizontal && parentSize > 0
                        && (nodeSize / parentSize) >= FULL_BLEED_RATIO_THRESHOLD;
                if (looksFullBleed) {
                    css.append("  ").append(leading).append(": ").append(round(leadOffset)).append("px;\n");
                    css.append("  ").append(trailing).append(": ").append(round(trailOffset)).append("px;\n");
                } else {
                    css.append("  ").append(leading).append(": ").append(round(leadOffset)).append("px;\n");
                    css.append("  ").append(sizeProp).append(": ").append(round(nodeSize)).append("px;\n");
                }
            }
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
                                       Map<String, String> imageUrlByNodeId, boolean sizeHandledByPosition) {
        writeSizing(node, parent, css, sizeHandledByPosition);
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

    private void writeSizing(DesignNode node, DesignNode parent, StringBuilder css, boolean sizeHandledByPosition) {
        SizingSpec sizing = sizingOf(node);
        boolean inFlex = isAutoFlex(parent) && layoutOf(node).getPositioning() != Positioning.ABSOLUTE;

        if (!sizeHandledByPosition) {
            writeDimension("width", sizing.getHorizontal(), sizing.getWidth(), css);
            writeDimension("height", sizing.getVertical(), sizing.getHeight(), css);
        }
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
        // .figma-page: khung ngoai, co gian theo man hinh (fluid width, kep tran boi kich
        // thuoc thiet ke goc tu Figma). .figma-canvas: giu nguyen kich thuoc pixel-perfect
        // goc va duoc mot doan script nho (HtmlGeneratorService.responsiveScript() cho HTML,
        // hoac src/lib/responsiveScale.js cho ban xuat React) co/gian bang transform: scale()
        // cho vua khung cha khi man hinh nho hon thiet ke (dien thoai/tablet).
        css.append(".figma-page {\n");
        css.append("  position: relative;\n");
        css.append("  width: 100%;\n");
        css.append("  max-width: calc(var(--figma-width) * 1px);\n");
        css.append("  margin: 0 auto;\n  background: #ffffff;\n  overflow: hidden;\n}\n\n");
        css.append(".figma-canvas {\n");
        css.append("  position: relative;\n  width: 100%;\n  transform-origin: top left;\n}\n\n");
        css.append(".figma-node { overflow: hidden; }\n\n");
        css.append("@media (max-width: 480px) {\n  .figma-node[data-figma-type=\"TEXT\"] { overflow-wrap: break-word; }\n}\n\n");
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