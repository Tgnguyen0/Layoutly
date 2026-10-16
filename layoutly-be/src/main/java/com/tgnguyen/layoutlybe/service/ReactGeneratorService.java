package com.tgnguyen.layoutlybe.service;

import com.tgnguyen.layoutlybe.model.ir.Bounds;
import com.tgnguyen.layoutlybe.model.ir.DesignNode;
import com.tgnguyen.layoutlybe.model.ir.NodeRole;
import com.tgnguyen.layoutlybe.model.ir.TransitionSpec;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ReactGeneratorService {

    @org.springframework.beans.factory.annotation.Value("${layoutly.renderer.page-consistency:false}")
    private boolean pageConsistency;
    public ReactGeneratorService() { }
    public ReactGeneratorService(boolean pageConsistency) { this.pageConsistency = pageConsistency; }

    // Ten component khong duoc trung voi ten da dung trong App.jsx (export default function App).
    private static final Set<String> RESERVED_COMPONENT_NAMES = Set.of("App");

    public String generate(DesignNode root) {
        List<GeneratedPage> pages = generatePages(root);
        return pages.isEmpty() ? emptyComponent() : pages.get(0).source();
    }

    public List<GeneratedPage> generatePages(DesignNode root) {
        List<DesignNode> pageRoots = findPageRoots(root);
        Map<String, Integer> usedNames = new LinkedHashMap<>();
        List<String> componentNames = new ArrayList<>();
        List<String> routes = new ArrayList<>();
        Map<String, String> routeByNodeId = new LinkedHashMap<>();

        // Luot 1: chot ten component + route cho tung trang, va ghi nho node nao thuoc trang nao
        // (de resolve linkTargetId cua Figma prototype sang route).
        for (int i = 0; i < pageRoots.size(); i++) {
            DesignNode pageRoot = pageRoots.get(i);
            String baseName = toComponentName(pageRoot.getName());
            if (RESERVED_COMPONENT_NAMES.contains(baseName)) baseName = baseName + "Page";
            int count = usedNames.merge(baseName, 1, Integer::sum);
            String componentName = count == 1 ? baseName : baseName + count;
            String route = i == 0 ? "/" : "/" + toFileName(componentName);
            componentNames.add(componentName);
            routes.add(route);
            indexNodes(pageRoot, route, routeByNodeId);
        }

        // Luot 2: sinh JSX, node co link sang trang khac se thanh <Link to="...">.
        List<GeneratedPage> pages = new ArrayList<>();
        for (int i = 0; i < pageRoots.size(); i++) {
            String componentName = componentNames.get(i);
            RenderContext ctx = new RenderContext(routeByNodeId, routes.get(i));
            String source = generatePage(pageRoots.get(i), componentName, ctx);
            pages.add(new GeneratedPage(componentName, toFileName(componentName) + ".jsx", source, routes.get(i)));
        }
        return pages;
    }

    private String generatePage(DesignNode node, String componentName, RenderContext ctx) {
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
        renderNode(node, 4, jsx, ctx);
        jsx.append("      </section>\n");
        jsx.append("    </main>\n");
        jsx.append("  )\n");
        jsx.append("}\n");
        StringBuilder imports = new StringBuilder();
        if (ctx.usesLink) imports.append("import { Link } from 'react-router-dom'\n");
        if (ctx.usesTransitionLink) imports.append("import TransitionLink from '../lib/TransitionLink.jsx'\n");
        if (imports.length() > 0) imports.append("\n");
        return imports + jsx.toString();
    }

    private void renderNode(DesignNode node, int depth, StringBuilder jsx, RenderContext ctx) {
        if (node == null) return;
        if ("DOCUMENT".equals(node.getType()) || "CANVAS".equals(node.getType())) {
            for (DesignNode child : childrenOf(node)) renderNode(child, depth, jsx, ctx);
            return;
        }

        String indent = "  ".repeat(depth);
        if (node.getRole() == NodeRole.INPUT || node.getRole() == NodeRole.CHECKBOX) {
            renderInput(node, depth, jsx, ctx.label);
            return;
        }
        // Node co Figma prototype NAVIGATE sang trang khac => <Link>. Khong long <Link> trong <Link>
        // (<a> long <a> la HTML khong hop le).
        LinkTarget link = ctx.linkDepth == 0 ? resolveLink(node, ctx) : null;
        String tag = link != null ? link.tag() : ("TEXT".equals(node.getType()) ? "p" : "div");
        if (link != null) {
            if ("Link".equals(link.tag())) ctx.usesLink = true;
            if ("TransitionLink".equals(link.tag())) ctx.usesTransitionLink = true;
        }

        jsx.append(indent).append("<").append(tag)
                .append(link != null ? " " + link.attr() + "=" + jsxString(link.value()) : "")
                .append(" className=\"").append(classFor(node)).append("\"")
                .append(" id=").append(jsxString(anchorId(node.getId())))
                .append(" data-figma-type=").append(jsxString(node.getType()))
                .append(" data-figma-name={").append(jsxString(node.getName())).append("}");

        if (link != null && link.css() != null) {
            TransitionSpec spec = node.getTransition();
            if (spec.type() != null) jsx.append(" data-transition=").append(jsxString(spec.type()));
            jsx.append(" data-duration=").append(jsxString(String.valueOf(link.css().durationMs())))
                    .append(" data-easing=").append(jsxString(link.css().easing()));
        }

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
        if (link != null) ctx.linkDepth++;
        String prevLabel = null; // TEXT dung truoc <input> cung cap = label cua no
        for (DesignNode child : children) {
            ctx.label = prevLabel;
            renderNode(child, depth + 1, jsx, ctx);
            if ("TEXT".equals(child.getType()) && child.getText() != null) prevLabel = child.getText();
        }
        if (link != null) ctx.linkDepth--;
        jsx.append(indent).append("</").append(tag).append(">\n");
    }

    private void renderInput(DesignNode node, int depth, StringBuilder jsx, String label) {
        boolean checkbox = node.getRole() == NodeRole.CHECKBOX;
        String placeholder = checkbox ? null : firstText(node);
        String type = checkbox ? "checkbox" : InputSupport.inferType(node.getName(), placeholder, label);
        jsx.append("  ".repeat(depth)).append("<input type=").append(jsxString(type))
                .append(" className=\"").append(classFor(node)).append("\"")
                .append(" id=").append(jsxString(anchorId(node.getId())))
                .append(" data-figma-type=").append(jsxString(node.getType()))
                .append(" data-figma-name={").append(jsxString(node.getName())).append("}");
        String fieldName = InputSupport.fieldName(label != null ? label : placeholder, node.getName());
        if (fieldName != null) jsx.append(" name=").append(jsxString(fieldName));
        if (placeholder != null) jsx.append(" placeholder=").append(jsxString(placeholder));
        String aria = label != null ? label : placeholder;
        if (aria != null) jsx.append(" aria-label=").append(jsxString(aria));
        jsx.append(" />\n");
    }

    private String firstText(DesignNode node) {
        if ("TEXT".equals(node.getType()) && node.getText() != null) return node.getText();
        for (DesignNode child : childrenOf(node)) {
            String t = firstText(child);
            if (t != null) return t;
        }
        return null;
    }

    /** Route cua trang ma node nay dieu huong toi; null neu khong co link hoac link ve chinh trang hien tai. */
    private record LinkTarget(String tag, String attr, String value, TransitionCssMapper.CssTransition css) {}

    private LinkTarget resolveLink(DesignNode node, RenderContext ctx) {
        String target = node.getLinkTargetId();
        if (target == null) return null;
        String route = ctx.routeByNodeId.get(target);
        if (route == null) return null;

        boolean samePage = route.equals(ctx.currentRoute);
        // SCROLL_TO cung trang: <a href="#node-x">, trinh duyet tu cuon (html { scroll-behavior: smooth })
        if ("SCROLL_TO".equals(node.getLinkNavigation()) && samePage) {
            return new LinkTarget("a", "href", "#" + anchorId(target), null);
        }
        if (samePage) return null;

        TransitionCssMapper.CssTransition css = TransitionCssMapper.toCss(node.getTransition());
        return new LinkTarget(css != null ? "TransitionLink" : "Link", "to", route, css);
    }

    private String anchorId(String nodeId) {
        return "node-" + toCssClassName(nodeId);   // phai giong HtmlGeneratorService.anchorId
    }

    private void indexNodes(DesignNode node, String route, Map<String, String> routeByNodeId) {
        if (node == null) return;
        if (node.getId() != null) routeByNodeId.put(node.getId(), route);
        for (DesignNode child : childrenOf(node)) indexNodes(child, route, routeByNodeId);
    }

    private List<DesignNode> findPageRoots(DesignNode root) {
        if (pageConsistency) return DesignRootSelector.exportPages(root);
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
            if (node.getStyle() != null && Boolean.TRUE.equals(node.getStyle().getClipsContent())) return;
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
        return "figma-node " + toFileName(node.getName()) + " node-" + toCssClassName(node.getId());
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

    /**
     * PHAI giong het CssGeneratorService.toClassName / HtmlGeneratorService.toClassName,
     * neu khong class "node-<id>" trong JSX se khong khop selector trong layoutly.css
     * (vd. id instance "I1:578;53923:27930").
     */
    private String toCssClassName(String value) {
        if (value == null || value.isBlank()) return "node";
        String slug = value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
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

    public record GeneratedPage(String componentName, String fileName, String source, String route) {
    }

    private static class RenderContext {
        private final Map<String, String> routeByNodeId;
        private final String currentRoute;
        private boolean usesLink;
        private boolean usesTransitionLink;
        private String label;
        private int linkDepth;

        private RenderContext(Map<String, String> routeByNodeId, String currentRoute) {
            this.routeByNodeId = routeByNodeId;
            this.currentRoute = currentRoute;
        }
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
