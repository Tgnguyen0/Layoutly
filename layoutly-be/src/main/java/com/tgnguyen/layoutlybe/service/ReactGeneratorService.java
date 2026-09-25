package com.tgnguyen.layoutlybe.service;

import com.tgnguyen.layoutlybe.model.ir.Bounds;
import com.tgnguyen.layoutlybe.model.ir.DesignNode;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReactGeneratorService {

    public String generate(DesignNode root) {
        List<GeneratedPage> pages = generatePages(root);
        return pages.isEmpty() ? emptyComponent() : pages.get(0).source();
    }

    public List<GeneratedPage> generatePages(DesignNode root) {
        List<DesignNode> pageRoots = findPageRoots(root);
        Map<String, Integer> usedNames = new LinkedHashMap<>();
        List<GeneratedPage> pages = new ArrayList<>();

        for (DesignNode pageRoot : pageRoots) {
            String baseName = toComponentName(pageRoot.getName());
            int count = usedNames.merge(baseName, 1, Integer::sum);
            String componentName = count == 1 ? baseName : baseName + count;
            String fileName = toFileName(componentName) + ".jsx";
            pages.add(new GeneratedPage(componentName, fileName, generatePage(pageRoot, componentName)));
        }
        return pages;
    }

    private String generatePage(DesignNode node, String componentName) {
        RenderBounds bounds = findRenderableBounds(node);
        double width = bounds != null ? bounds.width() : fallback(node.getBounds(), true, 1440);
        double height = bounds != null ? bounds.height() : fallback(node.getBounds(), false, 900);
        double offsetX = bounds != null ? bounds.minX : 0;
        double offsetY = bounds != null ? bounds.minY : 0;

        StringBuilder jsx = new StringBuilder();
        jsx.append("export default function ").append(componentName).append("() {\n");
        jsx.append("  return (\n");
        jsx.append("    <main className=\"figma-page\" style={{\n");
        jsx.append("      '--figma-width': ").append(round(width)).append(",\n");
        jsx.append("      '--figma-height': ").append(round(height)).append(",\n");
        jsx.append("      '--figma-offset-x': ").append(round(offsetX)).append(",\n");
        jsx.append("      '--figma-offset-y': ").append(round(offsetY)).append(",\n");
        jsx.append("    }}>\n");
        jsx.append("      <section className=\"figma-canvas\">\n");
        renderNode(node, 4, jsx);
        jsx.append("      </section>\n");
        jsx.append("    </main>\n");
        jsx.append("  )\n");
        jsx.append("}\n");
        return jsx.toString();
    }

    private void renderNode(DesignNode node, int depth, StringBuilder jsx) {
        if (node == null) return;
        if ("DOCUMENT".equals(node.getType()) || "CANVAS".equals(node.getType())) {
            for (DesignNode child : childrenOf(node)) renderNode(child, depth, jsx);
            return;
        }

        String indent = "  ".repeat(depth);
        String tag = "TEXT".equals(node.getType()) ? "p" : "div";
        jsx.append(indent).append("<").append(tag)
                .append(" className=\"").append(classFor(node)).append("\"")
                .append(" data-figma-type=").append(jsxString(node.getType()))
                .append(" data-figma-name=").append(jsxString(node.getName()));

        if ("TEXT".equals(node.getType()) && node.getText() != null) {
            jsx.append(">{").append(jsxString(node.getText())).append("}</").append(tag).append(">\n");
            return;
        }

        List<DesignNode> children = childrenOf(node);
        if (children.isEmpty()) {
            jsx.append(" />\n");
            return;
        }

        jsx.append(">\n");
        for (DesignNode child : children) renderNode(child, depth + 1, jsx);
        jsx.append(indent).append("</").append(tag).append(">\n");
    }

    private List<DesignNode> findPageRoots(DesignNode root) {
        if (root == null) return List.of();
        List<DesignNode> pages = new ArrayList<>();
        for (DesignNode child : childrenOf(root)) {
            if (!"CANVAS".equals(child.getType())) continue;
            List<DesignNode> topFrames = childrenOf(child).stream()
                    .filter(node -> "FRAME".equals(node.getType()))
                    .toList();
            if (topFrames.isEmpty()) pages.add(child);
            else pages.addAll(topFrames);
        }
        if (pages.isEmpty()) pages.add(root);
        return pages;
    }

    private RenderBounds findRenderableBounds(DesignNode root) {
        MutableBounds result = new MutableBounds();
        collectBounds(root, result);
        return result.hasValue
                ? new RenderBounds(result.minX, result.minY, result.maxX, result.maxY)
                : null;
    }

    private void collectBounds(DesignNode node, MutableBounds result) {
        if (node == null) return;
        Bounds bounds = node.getBounds();
        boolean renderable = !"DOCUMENT".equals(node.getType()) && !"CANVAS".equals(node.getType());
        if (renderable && bounds != null && bounds.x() != null && bounds.y() != null
                && bounds.width() != null && bounds.height() != null) {
            result.include(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height());
        }
        for (DesignNode child : childrenOf(node)) collectBounds(child, result);
    }

    private double fallback(Bounds bounds, boolean horizontal, double fallback) {
        if (bounds == null) return fallback;
        Double value = horizontal ? bounds.width() : bounds.height();
        return value != null ? value : fallback;
    }

    private List<DesignNode> childrenOf(DesignNode node) {
        return node.getChildren() != null ? node.getChildren() : List.of();
    }

    private String classFor(DesignNode node) {
        return "figma-node " + toFileName(node.getName()) + " node-" + toFileName(node.getId());
    }

    String toComponentName(String value) {
        if (value == null || value.isBlank()) return "LayoutlyPage";
        StringBuilder name = new StringBuilder();
        for (String part : ascii(value).split("[^a-zA-Z0-9]+")) {
            if (part.isBlank()) continue;
            name.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) name.append(part.substring(1));
        }
        if (name.isEmpty()) return "LayoutlyPage";
        if (!Character.isJavaIdentifierStart(name.charAt(0))) name.insert(0, "Page");
        return name.toString();
    }

    private String toFileName(String value) {
        if (value == null || value.isBlank()) return "node";
        String slug = ascii(value).replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .replaceAll("([a-zA-Z])([0-9])", "$1-$2")
                .replaceAll("([0-9])([a-zA-Z])", "$1-$2")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "node" : slug;
    }

    private String ascii(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('\u0111', 'd')
                .replace('\u0110', 'D');
    }

    private String jsxString(String value) {
        if (value == null) return "\"\"";
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t") + "\"";
    }

    private String emptyComponent() {
        return "export default function LayoutlyPage() {\n  return <main />\n}\n";
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record GeneratedPage(String componentName, String fileName, String source) {
    }

    private record RenderBounds(double minX, double minY, double maxX, double maxY) {
        double width() {
            return Math.max(1, maxX - minX);
        }

        double height() {
            return Math.max(1, maxY - minY);
        }
    }

    private static class MutableBounds {
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
    }
}
