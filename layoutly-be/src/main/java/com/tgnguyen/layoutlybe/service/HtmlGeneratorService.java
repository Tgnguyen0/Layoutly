package com.tgnguyen.layoutlybe.service;

import com.tgnguyen.layoutlybe.model.UINode;
import org.springframework.stereotype.Service;

@Service
public class HtmlGeneratorService {
    // Sinh HTML cau truc (chua co CSS) tu cay UINode.
    // Muc tieu tuan 7: chi quan tam cau truc long nhau dung, chua quan tam style.
    public String generate(UINode root) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"vi\">\n<head>\n")
                .append("<meta charset=\"UTF-8\">\n")
                .append("  <title>").append(escape(root.getName())).append("</title>\n")
                .append("  <link rel=\"stylesheet\" href=\"styles.css\">\n")
                .append("</head>\n<body>\n");

        for (UINode child : root.getChildren()) {
            renderNode(child, 1, sb);
        }

        sb.append("</body>\n</html>\n");
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

        String cssClass = toClassName(node.getType());
        sb.append(indent)
                .append("<").append(tag)
                .append(" class=\"").append(cssClass).append("\"")
                .append(" data-figma-type=\"").append(node.getType()).append("\">");

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
