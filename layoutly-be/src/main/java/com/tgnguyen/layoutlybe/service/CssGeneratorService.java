package com.tgnguyen.layoutlybe.service;

import com.tgnguyen.layoutlybe.model.UINode;
import org.springframework.stereotype.Service;

@Service
public class CssGeneratorService {

    // Sinh CSS cho toan bo cay. Dung absolute positioning tam thoi (chua co Auto Layout/Flexbox - tuan 5).
    public String generate(UINode root) {
        StringBuilder sb = new StringBuilder();
        sb.append("* { box-sizing: border-box; margin: 0; padding: 0; }\n\n");

        for (UINode child : root.getChildren()) {
            walk(child, root, sb);
        }
        return sb.toString();
    }

    private void walk(UINode node, UINode parent, StringBuilder sb) {
        // CANVAS khong render, di thang xuong children
        if ("CANVAS".equals(node.getType())) {
            for (UINode child : node.getChildren()) {
                walk(child, parent, sb);
            }
            return;
        }

        sb.append(".").append(toClassName(node.getName())).append(" {\n");

        // Vi tri: tinh tuong doi so voi parent, vi parent se duoc dat position:relative
        if (node.getX() != null && parent.getX() != null) {
            sb.append("  position: absolute;\n");
            sb.append("  left: ").append(round(node.getX() - safe(parent.getX()))).append("px;\n");
            sb.append("  top: ").append(round(node.getY() - safe(parent.getY()))).append("px;\n");
        } else {
            // Node goc cung (khong co parent toa do) - lam moc position:relative de chua cac con absolute
            sb.append("  position: relative;\n");
        }

        if (node.getWidth() != null) sb.append("  width: ").append(round(node.getWidth())).append("px;\n");
        if (node.getHeight() != null) sb.append("  height: ").append(round(node.getHeight())).append("px;\n");

        if (node.getBackgroundColor() != null) sb.append("  background-color: ").append(node.getBackgroundColor()).append(";\n");
        if (node.getOpacity() != null && node.getOpacity() < 1.0) sb.append("  opacity: ").append(node.getOpacity()).append(";\n");
        if (node.getCornerRadius() != null && node.getCornerRadius() > 0) sb.append("  border-radius: ").append(round(node.getCornerRadius())).append("px;\n");
        if (node.getBorderColor() != null && node.getBorderWidth() != null) {
            sb.append("  border: ").append(round(node.getBorderWidth())).append("px solid ").append(node.getBorderColor()).append(";\n");
        }

        if ("TEXT".equals(node.getType())) {
            if (node.getFontFamily() != null) sb.append("  font-family: '").append(node.getFontFamily()).append("', sans-serif;\n");
            if (node.getFontSize() != null) sb.append("  font-size: ").append(round(node.getFontSize())).append("px;\n");
            if (node.getFontWeight() != null) sb.append("  font-weight: ").append(node.getFontWeight().intValue()).append(";\n");
            if (node.getLineHeight() != null) sb.append("  line-height: ").append(round(node.getLineHeight())).append("px;\n");
            if (node.getLetterSpacing() != null) sb.append("  letter-spacing: ").append(round(node.getLetterSpacing())).append("px;\n");
        }

        sb.append("}\n\n");

        for (UINode child : node.getChildren()) {
            walk(child, node, sb);
        }
    }

    private double safe(Double d) { return d != null ? d : 0; }
    private double round(double d) { return Math.round(d * 100.0) / 100.0; }

    private String toClassName(String name) {
        if (name == null || name.isBlank()) return "node";
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "node" : slug;
    }
}