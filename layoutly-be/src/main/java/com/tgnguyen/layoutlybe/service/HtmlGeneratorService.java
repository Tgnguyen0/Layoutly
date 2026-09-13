package com.tgnguyen.layoutlybe.service;

import com.tgnguyen.layoutlybe.model.UINode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public Map<String, String> generateAuto(UINode root) {
        Map<String, String> result = new LinkedHashMap<>();
        Map<String, Integer> usedNames = new LinkedHashMap<>();
        if (root == null || root.getChildren() == null) return result;

        for (UINode canvas : root.getChildren()) {
            if (!"CANVAS".equals(canvas.getType())) continue;

            List<UINode> topFrames = new ArrayList<>();
            if (canvas.getChildren() != null) {
                for (UINode child : canvas.getChildren()) {
                    if ("FRAME".equals(child.getType())) topFrames.add(child);
                }
            }

            if (topFrames.size() <= 1) {
                // Chi co 1 (hoac khong co) Frame ngoai cung -> xem nhu 1 trang hoan chinh
                result.put(uniqueFilename(canvas, usedNames), generateSingleNodeDocument(canvas));
            } else {
                // Nhieu Frame doc lap tren cung 1 Page -> tach rieng tung thiet ke
                for (UINode frame : topFrames) {
                    result.put(uniqueFilename(frame, usedNames), generateSingleNodeDocument(frame));
                }
            }
        }
        return result;
    }

    /**
     * Tach HTML thanh nhieu file rieng, dua vao thuoc tinh "type" cua UINode.
     * Vi du targetType = "FRAME": moi node co type = FRAME trong toan bo cay (bat ke
     * dang o Canvas nao, long sau bao nhieu tang) se thanh 1 file .html doc lap,
     * thay vi gop chung tat ca vao 1 file index.html duy nhat nhu generate(root) o tren.
     *
     * Luu y quan trong: neu 1 FRAME nam long BEN TRONG 1 FRAME khac cung khop type,
     * ca 2 se deu tach thanh file rieng - frame cha van chua nguyen frame con o trong
     * no (render lai lan nua), khong bi "mat" node con. Day la lua chon co chu dich:
     * de nguoi dung tu quyet dinh dung file nao (file cha co day du, hay tung file con
     * rieng le de tai su dung nhu component).
     *
     * @return Map filename (vi du "primary-button.html") -> noi dung HTML day du cua node do.
     *         Ten file tu dong danh so lai (-2, -3...) neu trung ten sau khi sanitize.
     */
    public Map<String, String> generateByType(UINode root, String targetType) {
        Map<String, String> result = new LinkedHashMap<>();
        Map<String, Integer> usedNames = new LinkedHashMap<>();
        collectByType(root, targetType, result, usedNames);
        return result;
    }

    private void collectByType(UINode node, String targetType,
                                Map<String, String> result, Map<String, Integer> usedNames) {
        if (node == null) return;

        if (targetType.equals(node.getType())) {
            String filename = uniqueFilename(node, usedNames);
            result.put(filename, generateSingleNodeDocument(node));
            // DUNG DE QUY o day - node con ben trong (du cung khop type) da nam tron
            // trong file vua tach, khong can tach rieng nua. Neu di tiep xuong duoi,
            // 1 Frame man hinh chua nhieu Frame nho (button wrapper, card wrapper...)
            // se bi tach du thua hang chuc file khong phai la "man hinh" that su.
            // Muon tach ca node long ben trong (hanh vi cu), doi return; thanh
            // duyet tiep xuong duoi binh thuong.
            return;
        }

        if (node.getChildren() != null) {
            for (UINode child : node.getChildren()) {
                collectByType(child, targetType, result, usedNames);
            }
        }
    }

    /** Sinh 1 file .html hoan chinh, lay dung 1 node lam goc (thay vi toan bo DOCUMENT). */
    private String generateSingleNodeDocument(UINode node) {
        StringBuilder sb = new StringBuilder();

        // QUAN TRONG: node duoc tach (VD: CANVAS) co the KHONG co absoluteBoundingBox rieng
        // (Figma khong gan x/y/width/height cho CANVAS - no la "trang vo han"). Neu dung
        // thang node.getWidth()/getHeight() se ra null -> fallback sai ve 1440x900 co dinh,
        // cat mat phan noi dung dai hon, va offset hardcode 0,0 lam lech toa do cac con.
        // Phai tinh lai bounds thuc te tu chinh cac node con (giong het cach generate()
        // o tren dang lam cho toan bo Document) de ra dung kich thuoc + offset that.
        Bounds bounds = findRenderableBounds(node);
        double width = bounds != null ? bounds.width() : (node.getWidth() != null ? node.getWidth() : 1440);
        double height = bounds != null ? bounds.height() : (node.getHeight() != null ? node.getHeight() : 900);
        double offsetX = bounds != null ? bounds.minX : 0;
        double offsetY = bounds != null ? bounds.minY : 0;

        sb.append("<!DOCTYPE html>\n<html lang=\"vi\">\n<head>\n")
                .append("<meta charset=\"UTF-8\">\n")
                .append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
                .append("  <meta name=\"figma-width\" content=\"").append(round(width)).append("\">\n")
                .append("  <meta name=\"figma-height\" content=\"").append(round(height)).append("\">\n")
                .append("  <title>").append(escape(node.getName())).append("</title>\n")
                .append("  <link rel=\"stylesheet\" href=\"styles.css\">\n")
                .append("</head>\n<body>\n");
        sb.append("<main class=\"figma-page\" style=\"--figma-width: ")
                .append(round(width))
                .append("; --figma-height: ")
                .append(round(height))
                .append("; --figma-offset-x: ")
                .append(round(offsetX))
                .append("; --figma-offset-y: ")
                .append(round(offsetY))
                .append(";\">\n")
                .append(" <section class=\"figma-canvas\">\n");

        renderNode(node, 2, sb);

        sb.append(" </section>\n</main>\n</body>\n</html>\n");
        return sb.toString();
    }

    private String uniqueFilename(UINode node, Map<String, Integer> usedNames) {
        String base = toClassName(node.getName());
        int count = usedNames.merge(base, 1, Integer::sum);
        return (count > 1 ? base + "-" + count : base) + ".html";
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
