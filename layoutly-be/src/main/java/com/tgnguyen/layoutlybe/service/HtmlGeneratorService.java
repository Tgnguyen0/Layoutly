package com.tgnguyen.layoutlybe.service;

import com.tgnguyen.layoutlybe.model.UINode;
import org.springframework.stereotype.Service;

@Service
public class HtmlGeneratorService {
    // Sinh HTML cau truc (chua co CSS) tu cay UINode.
    // Muc tieu tuan 7: chi quan tam cau truc long nhau dung, chua quan tam style.
    public String generate(UINode root) {
        StringBuilder sb = new StringBuilder();
        Bounds viewportBounds = findRenderableBounds(root);
        double viewportWidth = viewportBounds != null ? viewportBounds.width() : 1440;
        double viewportHeight = viewportBounds != null ? viewportBounds.height() : 900;
        double viewportOffsetX = viewportBounds != null ? viewportBounds.minX : 0;
        double viewportOffsetY = viewportBounds != null ? viewportBounds.minY : 0;

        sb.append("<!DOCTYPE html>\n<html lang=\"vi\">\n<head>\n")
                .append("<meta charset=\"UTF-8\">\n")
                .append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
                .append("  <meta name=\"figma-width\" content=\"").append(round(viewportWidth)).append("\">\n")
                .append("  <meta name=\"figma-height\" content=\"").append(round(viewportHeight)).append("\">\n")
                .append("  <title>").append(escape(root.getName())).append("</title>\n")
                .append("  <link rel=\"stylesheet\" href=\"styles.css\">\n")
                .append("</head>\n<body>\n");
        sb.append("<main class=\"figma-page\" style=\"--figma-width: ")
                .append(round(viewportWidth))
                .append("; --figma-height: ")
                .append(round(viewportHeight))
                .append("; --figma-offset-x: ")
                .append(round(viewportOffsetX))
                .append("; --figma-offset-y: ")
                .append(round(viewportOffsetY))
                .append(";\">\n")
                .append(" <section class=\"figma-canvas\">\n");

        for (UINode child : root.getChildren()) {
            renderNode(child, 2, sb);
        }

        sb.append(" </section>\n</main>\n</body>\n</html>\n");
        return sb.toString();
    }

    private void renderNode(UINode node, int depth, StringBuilder sb) {
        String indent = " ".repeat(depth);
        String tag = tagFor(node.getType());

        // CANVAS (page) khong xuat ra the HTML, chi duyet tiep xuong children cua no
        if ("CANVAS".equals(node.getType())) {
            for (UINode child : node.getChildren()) {
                renderNode(child, depth, sb);
            }
            return;
        }

        String cssClass = classFor(node);
        sb.append(indent)
                .append("<").append(tag)
                .append(" class=\"").append(cssClass).append("\"")
                .append(" data-figma-type=\"").append(escape(node.getType())).append("\"")
                .append(" data-figma-name=\"").append(escape(node.getName())).append("\">");

        if ("TEXT".equals(node.getType()) && node.getCharacters() != null) {
            sb.append(escape(node.getCharacters()));
        } else if (!node.getChildren().isEmpty()) {
            sb.append("\n");

            for (UINode child : node.getChildren()) {
                renderNode(child, depth + 1, sb);
            }

            sb.append(indent);
        }

        sb.append("</").append(tag).append(">\n");
    }

    // Anh xa loai node Figma sang the HTML phu hop
    private String tagFor(String figmaType) {
        if (figmaType == null) return "div";
        return switch (figmaType) {
            case "TEXT" -> "p";
            default -> "div"; // FRAME, GROUP, VECTOR, RECTANGLE, ELLIPSE, INSTANCE, COMPONENT...
        };
    }

    private String classFor(UINode node) {
        return "figma-node " + toClassName(node.getName()) + " node-" + toClassName(node.getId());
    }

    private Bounds findRenderableBounds(UINode root) {
        Bounds bounds = new Bounds();
        collectRenderableBounds(root, bounds);
        return bounds.hasValue ? bounds : null;
    }

    private void collectRenderableBounds(UINode node, Bounds bounds) {
        if (node == null) return;

        boolean renderable = !"DOCUMENT".equals(node.getType()) && !"CANVAS".equals(node.getType());
        if (renderable && node.getX() != null && node.getY() != null && node.getWidth() != null && node.getHeight() != null) {
            bounds.include(node.getX(), node.getY(), node.getX() + node.getWidth(), node.getY() + node.getHeight());
        }

        for (UINode child : node.getChildren()) {
            collectRenderableBounds(child, bounds);
        }
    }

    private static class Bounds {
        private boolean hasValue;
        private double minX;
        private double minY;
        private double maxX;
        private double maxY;

        private void include(double x1, double y1, double x2, double y2) {
            if (!hasValue) {
                minX = x1;
                minY = y1;
                maxX = x2;
                maxY = y2;
                hasValue = true;
                return;
            }

            minX = Math.min(minX, x1);
            minY = Math.min(minY, y1);
            maxX = Math.max(maxX, x2);
            maxY = Math.max(maxY, y2);
        }

        private double width() {
            return Math.max(1, maxX - minX);
        }

        private double height() {
            return Math.max(1, maxY - minY);
        }
    }

    private double round(double d) {
        return Math.round(d * 100.0) / 100.0;
    }

    // Chuyen ten layer Figma (vd: "Primary Button / Large") thanh class CSS hop le
    private String toClassName(String name) {
        if (name == null || name.isEmpty()) return "node";

        String slug = name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "node" : slug;
    }

    private String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
