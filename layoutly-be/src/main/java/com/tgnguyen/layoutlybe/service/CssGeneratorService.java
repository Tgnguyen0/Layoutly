package com.tgnguyen.layoutlybe.service;

import com.tgnguyen.layoutlybe.model.UINode;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CssGeneratorService {

    // Sinh CSS cho toan bo cay. Dung absolute positioning tam thoi (chua co Auto Layout/Flexbox - tuan 5).
    public String generate(UINode root) {
        return generate(root, Map.of());
    }

    public String generate(UINode root, Map<String, String> imageUrlByNodeId) {
        StringBuilder sb = new StringBuilder();
        sb.append("* { box-sizing: border-box; margin: 0; padding: 0; }\n\n");
        sb.append("html, body { min-height: 100%; }\n");
        sb.append("body {\n");
        sb.append("  font-family: Arial, sans-serif;\n");
        sb.append("  background: #1e1e1e;\n");
        sb.append("  overflow-x: hidden;\n");
        sb.append("}\n\n");
        sb.append(".figma-page {\n");
        sb.append("  width: 100%;\n");
        sb.append("  max-width: calc(var(--figma-width) * 1px);\n");
        sb.append("  height: calc(var(--figma-height) * 1px * min(1, 100vw / (var(--figma-width) * 1px)));\n");
        sb.append("  margin: 0 auto;\n");
        sb.append("  background: #ffffff;\n");
        sb.append("  overflow: hidden;\n");
        sb.append("}\n\n");
        sb.append(".figma-canvas {\n");
        sb.append("  position: relative;\n");
        sb.append("  width: calc(var(--figma-width) * 1px);\n");
        sb.append("  height: calc(var(--figma-height) * 1px);\n");
        sb.append("  transform: scale(min(1, 100vw / (var(--figma-width) * 1px)));\n");
        sb.append("  transform-origin: top left;\n");
        sb.append("}\n\n");
        sb.append(".figma-node { overflow: hidden; }\n\n");

        for (UINode child : root.getChildren()) {
            walk(child, root, sb, imageUrlByNodeId);
        }
        return sb.toString();
    }

    private void walk(UINode node, UINode parent, StringBuilder sb, Map<String, String> imageUrlByNodeId) {
        // CANVAS khong render, di thang xuong children
        if ("CANVAS".equals(node.getType())) {
            for (UINode child : node.getChildren()) {
                walk(child, parent, sb, imageUrlByNodeId);
            }
            return;
        }

        sb.append(".node-").append(toClassName(node.getId())).append(" {\n");

        // Vi tri: tinh tuong doi so voi parent, vi parent se duoc dat position:relative
        if (node.getX() != null && parent.getX() != null) {
            sb.append("  position: absolute;\n");
            sb.append("  left: ").append(round(node.getX() - safe(parent.getX()))).append("px;\n");
            sb.append("  top: ").append(round(node.getY() - safe(parent.getY()))).append("px;\n");
        } else if (node.getX() != null && node.getY() != null) {
            sb.append("  position: absolute;\n");
            sb.append("  left: calc(").append(round(node.getX())).append("px - (var(--figma-offset-x) * 1px));\n");
            sb.append("  top: calc(").append(round(node.getY())).append("px - (var(--figma-offset-y) * 1px));\n");
        } else {
            // Node goc cung (khong co parent toa do) - lam moc position:relative de chua cac con absolute
            sb.append("  position: relative;\n");
        }

        if (node.getWidth() != null) sb.append("  width: ").append(round(node.getWidth())).append("px;\n");
        if (node.getHeight() != null) sb.append("  height: ").append(round(node.getHeight())).append("px;\n");

        String imageUrl = imageUrlByNodeId.get(node.getId());
        if (imageUrl != null && !imageUrl.isBlank()) {
            sb.append("  background-image: url(\"").append(escapeCssUrl(imageUrl)).append("\");\n");
            sb.append("  background-size: cover;\n");
            sb.append("  background-position: center;\n");
            sb.append("  background-repeat: no-repeat;\n");
        } else if (node.getBackgroundColor() != null) {
            if ("TEXT".equals(node.getType())) {
                sb.append("  color: ").append(node.getBackgroundColor()).append(";\n");
            } else {
                sb.append("  background-color: ").append(node.getBackgroundColor()).append(";\n");
            }
        }
        if (node.getOpacity() != null && node.getOpacity() < 1.0) sb.append("  opacity: ").append(node.getOpacity()).append(";\n");
        if (node.getCornerRadius() != null && node.getCornerRadius() > 0) sb.append("  border-radius: ").append(round(node.getCornerRadius())).append("px;\n");
        if (node.getBorderColor() != null && node.getBorderWidth() != null) {
            sb.append("  border: ").append(round(node.getBorderWidth())).append("px solid ").append(node.getBorderColor()).append(";\n");
        }

        if ("TEXT".equals(node.getType())) {
            sb.append("  white-space: pre-wrap;\n");
            if (node.getFontFamily() != null) sb.append("  font-family: '").append(node.getFontFamily()).append("', sans-serif;\n");
            if (node.getFontSize() != null) sb.append("  font-size: ").append(round(node.getFontSize())).append("px;\n");
            if (node.getFontWeight() != null) sb.append("  font-weight: ").append(node.getFontWeight().intValue()).append(";\n");
            if (node.getLineHeight() != null) sb.append("  line-height: ").append(round(node.getLineHeight())).append("px;\n");
            if (node.getLetterSpacing() != null) sb.append("  letter-spacing: ").append(round(node.getLetterSpacing())).append("px;\n");
        }

        sb.append("}\n\n");

        for (UINode child : node.getChildren()) {
            walk(child, node, sb, imageUrlByNodeId);
        }
    }

    private double safe(Double d) { return d != null ? d : 0; }
    private double round(double d) { return Math.round(d * 100.0) / 100.0; }

    private String toClassName(String name) {
        if (name == null || name.isBlank()) return "node";
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "node" : slug;
    }

    private String escapeCssUrl(String url) {
        return url.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
