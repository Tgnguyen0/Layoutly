package com.tgnguyen.layoutlybe.service;

import com.tgnguyen.layoutlybe.model.UINode;
import com.tgnguyen.layoutlybe.model.ir.DesignNode;
import com.tgnguyen.layoutlybe.model.ir.DesignNodeMapper;
import com.tgnguyen.layoutlybe.model.ir.NodeRole;
import com.tgnguyen.layoutlybe.model.ir.TransitionSpec;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class HtmlGeneratorService {

    /**
     * Script gon nhe, khong phu thuoc framework: co/gian toan bo ".figma-canvas"
     * (giu nguyen ty le, khong vo bo cuc) bang transform: scale() de vua chieu
     * rong cua ".figma-page" tren moi kich thuoc man hinh (dien thoai, tablet,
     * desktop). Duoc dung chung cho ca ban xuat HTML tinh va du an React.
     */
    public static String responsiveScript() {
        return "(function () {\n"
                + "  function applyLayoutlyResponsiveScale() {\n"
                + "    var pages = document.querySelectorAll('.figma-page');\n"
                + "    for (var i = 0; i < pages.length; i++) {\n"
                + "      var page = pages[i];\n"
                + "      var canvas = page.querySelector('.figma-canvas');\n"
                + "      if (!canvas) continue;\n"
                + "      var designWidth = parseFloat(page.style.getPropertyValue('--figma-width'));\n"
                + "      var designHeight = parseFloat(page.style.getPropertyValue('--figma-height'));\n"
                + "      if (!designWidth) continue;\n"
                + "      canvas.style.width = designWidth + 'px';\n"
                + "      if (designHeight) canvas.style.minHeight = designHeight + 'px';\n"
                + "      var available = page.clientWidth || window.innerWidth;\n"
                + "      var scale = available > 0 ? Math.min(1, available / designWidth) : 1;\n"
                + "      canvas.style.transform = 'scale(' + scale + ')';\n"
                + "      page.style.height = designHeight ? (designHeight * scale) + 'px' : '';\n"
//                + "var available = page.clientWidth || window.innerWidth;\n"
//                + "var scale = Math.min(available / designWidth, designHeight ? window.innerHeight / designHeight : Infinity);\n"
//                + "canvas.style.transform = 'scale(' + scale + ')';\n"
//                + "canvas.style.marginLeft = Math.max(0, (available - designWidth * scale) / 2) + 'px';\n"
//                + "page.style.height = designHeight ? (designHeight * scale) + 'px' : '';\n"
                + "    }\n"
                + "  }\n"
                + "  window.addEventListener('resize', applyLayoutlyResponsiveScale);\n"
                + "  window.addEventListener('orientationchange', applyLayoutlyResponsiveScale);\n"
                + "  if (document.readyState === 'loading') {\n"
                + "    document.addEventListener('DOMContentLoaded', applyLayoutlyResponsiveScale);\n"
                + "  } else {\n"
                + "    applyLayoutlyResponsiveScale();\n"
                + "  }\n"
                + "  window.__layoutlyApplyResponsiveScale = applyLayoutlyResponsiveScale;\n"
                + "})();\n";
    }

    public String generate(DesignNode root) {
        return generate(DesignNodeMapper.toLegacy(root));
    }

    public Map<String, String> generateAuto(DesignNode root) {
        return generateAuto(DesignNodeMapper.toLegacy(root));
    }

    public Map<String, String> generateByType(DesignNode root, String targetType) {
        return generateByType(DesignNodeMapper.toLegacy(root), targetType);
    }

    public String generateForNode(DesignNode node) {
        return generateSingleNodeDocument(DesignNodeMapper.toLegacy(node));
    }

    // Sinh HTML cau truc (chua co CSS) tu cay UINode.
    // Muc tieu tuan 7: chi quan tam cau truc long nhau dung, chua quan tam style.
    public String generate(UINode root) {
        if (root != null && !"DOCUMENT".equals(root.getType()) && !"CANVAS".equals(root.getType())) {
            return generateSingleNodeDocument(root);
        }
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
            renderNode(child, 2, sb, null);
        }

        sb.append(" </section>\n</main>\n")
                .append("<script>\n").append(responsiveScript()).append("</script>\n")
                .append("</body>\n</html>\n");
        return sb.toString();
    }

    public Map<String, String> generateAuto(UINode root) {
        Map<String, String> result = new LinkedHashMap<>();
        Map<String, Integer> usedNames = new LinkedHashMap<>();
        if (root == null || root.getChildren() == null) return result;

        Map<String, String> fileByNodeId = new HashMap<>();
        Map<String, UINode> rootByFile = new LinkedHashMap<>();

        if (!"DOCUMENT".equals(root.getType()) && !"CANVAS".equals(root.getType())) {
            String filename = uniqueFilename(root, usedNames);
            rootByFile.put(filename, root);
            indexNodes(root, filename, fileByNodeId);
        }

        // Luot 1: dat ten file cho tung trang + danh chi muc node -> file
        List<UINode> canvases = "CANVAS".equals(root.getType()) ? List.of(root) : root.getChildren();
        for (UINode canvas : canvases) {
            if (!"CANVAS".equals(canvas.getType())) continue;

            List<UINode> topFrames = new ArrayList<>();
            if (canvas.getChildren() != null) {
                for (UINode child : canvas.getChildren()) {
                    if ("FRAME".equals(child.getType())) topFrames.add(child);
                }
            }

            if (topFrames.size() <= 1 || DesignRootSelector.continuousSections(DesignNodeMapper.fromLegacy(canvas))) {
                // Chi co 1 (hoac khong co) Frame ngoai cung -> xem nhu 1 trang hoan chinh
                String filename = uniqueFilename(canvas, usedNames);
                rootByFile.put(filename, canvas);
                indexNodes(canvas, filename, fileByNodeId);
            } else {
                // Nhieu Frame doc lap tren cung 1 Page -> tach rieng tung thiet ke
                for (UINode frame : topFrames) {
                    String filename = uniqueFilename(frame, usedNames);
                    rootByFile.put(filename, frame);
                    indexNodes(frame, filename, fileByNodeId);
                }
            }
        }

        // Luot 2: sinh HTML, truyen kem ngu canh de buoc 4 tra link
        Map<String, TransitionSpec> incoming = collectIncomingTransitions(rootByFile, fileByNodeId);
        for (Map.Entry<String, UINode> entry : rootByFile.entrySet()) {
            LinkContext ctx = new LinkContext(fileByNodeId, entry.getKey(), incoming);
            result.put(entry.getKey(), generateSingleNodeDocument(entry.getValue(), ctx));
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
        Map<String, Integer> usedNames = new LinkedHashMap<>();
        Map<String, UINode> rootByFile = new LinkedHashMap<>();
        Map<String, String> fileByNodeId = new HashMap<>();

        // Luot 1: gom node can tach + danh chi muc node -> file
        collectByType(root, targetType, rootByFile, usedNames, fileByNodeId);

        // Luot 2: sinh HTML kem ngu canh de tra link
        Map<String, String> result = new LinkedHashMap<>();
        Map<String, TransitionSpec> incoming = collectIncomingTransitions(rootByFile, fileByNodeId);
        for (Map.Entry<String, UINode> entry : rootByFile.entrySet()) {
            LinkContext ctx = new LinkContext(fileByNodeId, entry.getKey(), incoming);
            result.put(entry.getKey(), generateSingleNodeDocument(entry.getValue(), ctx));
        }
        return result;
    }

    private void collectByType(UINode node, String targetType,
                               Map<String, UINode> rootByFile,
                               Map<String, Integer> usedNames,
                               Map<String, String> fileByNodeId) {
        if (node == null) return;

        if (targetType.equals(node.getType())) {
            String filename = uniqueFilename(node, usedNames);
            rootByFile.put(filename, node);
            indexNodes(node, filename, fileByNodeId);
            // DUNG DE QUY o day - giu nguyen y do cu: node con ben trong da nam tron trong file nay
            return;
        }

        if (node.getChildren() != null) {
            for (UINode child : node.getChildren()) {
                collectByType(child, targetType, rootByFile, usedNames, fileByNodeId);
            }
        }
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

    private String generateSingleNodeDocument(UINode node) {
        return generateSingleNodeDocument(node, null);
    }

    /** Sinh 1 file .html hoan chinh, lay dung 1 node lam goc (thay vi toan bo DOCUMENT). */
    private String generateSingleNodeDocument(UINode node, LinkContext ctx) {
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
                .append(viewTransitionStyle(ctx))
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

        renderNode(node, 2, sb, ctx);

        sb.append(" </section>\n</main>\n")
                .append("<script>\n").append(responsiveScript()).append("</script>\n")
                .append("</body>\n</html>\n");
        return sb.toString();
    }

    private String viewTransitionStyle(LinkContext ctx) {
        if (ctx == null || ctx.incomingTransitionByFile().isEmpty()) return "";
        TransitionSpec spec = ctx.incomingTransitionByFile().get(ctx.currentFile());
        // Cross-document view transition can CA trang nguon lan trang dich opt-in,
        // nen trang khong co transition di vao van phai co @view-transition.
        String css = spec != null
                ? CssGeneratorService.viewTransitionCss(spec, "LEFT")
                : "@view-transition { navigation: auto; }\n";
        return css.isEmpty() ? "" : "  <style>\n" + css + "  </style>\n";
    }

    private String uniqueFilename(UINode node, Map<String, Integer> usedNames) {
        String base = toClassName(node.getName());
        int count = usedNames.merge(base, 1, Integer::sum);
        return (count > 1 ? base + "-" + count : base) + ".html";
    }

    private void renderNode(UINode node, int depth, StringBuilder sb, LinkContext ctx) {
        renderNode(node, depth, sb, ctx, null);
    }

    private void renderNode(UINode node, int depth, StringBuilder sb, LinkContext ctx, String label) {
        String indent = " ".repeat(depth);

        // CANVAS (page) khong xuat ra the HTML, chi duyet tiep xuong children cua no
        if ("CANVAS".equals(node.getType())) {
            for (UINode child : node.getChildren()) {
                renderNode(child, depth, sb, ctx);
            }
            return;
        }

        if (node.getRole() == NodeRole.INPUT || node.getRole() == NodeRole.CHECKBOX) {
            renderInput(node, indent, sb, label);
            return;
        }
        String tag = tagFor(node.getRole());
        boolean scroll = "SCROLL_TO".equals(node.getLinkNavigation());
        String href = scroll ? resolveScrollHref(node, ctx) : resolveHref(node, ctx);
        // <a> long <a> la HTML khong hop le: parser tu dong dong the <a> ngoai, lam cac con
        // mat containing block (menu bi tut ve goc trai tren). Neu ben trong da co link thi
        // link con (cu the hon) thang, node cha khong render link.
        if (href != null && hasLinkDescendant(node, ctx)) href = null;
        if (href != null && !"button".equals(tag)) tag = "a";

        String cssClass = classFor(node);
        sb.append(indent)
                .append("<").append(tag)
                .append(" class=\"").append(cssClass).append("\"")
                .append(" id=\"").append(anchorId(node.getId())).append("\"")
                .append(" data-figma-type=\"").append(escape(node.getType())).append("\"")
                .append(" data-figma-name=\"").append(escape(node.getName())).append("\"");

        if (href != null) {
            if ("button".equals(tag)) {
                sb.append(" onclick=\"location.href='").append(href).append("'\"");
            } else {
                sb.append(" href=\"").append(href).append("\"");
            }
            appendTransitionAttrs(node, sb);   // <-- thêm dòng này
        }
        sb.append(">");

        if ("TEXT".equals(node.getType()) && node.getCharacters() != null) {
            sb.append(escape(node.getCharacters()));
        } else if (!node.getChildren().isEmpty()) {
            sb.append("\n");
            String prevLabel = null; // TEXT dung truoc <input> cung cap = label cua no
            for (UINode child : node.getChildren()) {
                renderNode(child, depth + 1, sb, ctx, prevLabel);
                if ("TEXT".equals(child.getType()) && child.getCharacters() != null) prevLabel = child.getCharacters();
            }
            sb.append(indent);
        }

        sb.append("</").append(tag).append(">\n");
    }

    private void renderInput(UINode node, String indent, StringBuilder sb, String label) {
        boolean checkbox = node.getRole() == NodeRole.CHECKBOX;
        String placeholder = checkbox ? null : firstText(node);
        String type = checkbox ? "checkbox" : InputSupport.inferType(node.getName(), placeholder, label);
        sb.append(indent).append("<input type=\"").append(type).append("\"")
                .append(" class=\"").append(classFor(node)).append("\"")
                .append(" id=\"").append(anchorId(node.getId())).append("\"")
                .append(" data-figma-type=\"").append(escape(node.getType())).append("\"")
                .append(" data-figma-name=\"").append(escape(node.getName())).append("\"");
        String fieldName = InputSupport.fieldName(label != null ? label : placeholder, node.getName());
        if (fieldName != null) sb.append(" name=\"").append(escape(fieldName)).append("\"");
        if (placeholder != null) sb.append(" placeholder=\"").append(escape(placeholder)).append("\"");
        String aria = label != null ? label : placeholder;
        if (aria != null) sb.append(" aria-label=\"").append(escape(aria)).append("\"");
        sb.append(">\n");
    }

    private String firstText(UINode node) {
        if ("TEXT".equals(node.getType()) && node.getCharacters() != null) return node.getCharacters();
        for (UINode child : node.getChildren()) {
            String t = firstText(child);
            if (t != null) return t;
        }
        return null;
    }

    // Anh xa loai node Figma sang the HTML phu hop
    private String tagFor(NodeRole role) {
        if (role == null) return "div";
        return switch (role) {
            case BUTTON -> "button";
            case TEXT -> "p";
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
            if (Boolean.TRUE.equals(node.getClipsContent())) return;
        }

        for (UINode child : node.getChildren()) {
            collectRenderableBounds(child, bounds);
        }
    }

    private String hrefOf(UINode node, LinkContext ctx) {
        return "SCROLL_TO".equals(node.getLinkNavigation())
                ? resolveScrollHref(node, ctx) : resolveHref(node, ctx);
    }

    private boolean hasLinkDescendant(UINode node, LinkContext ctx) {
        if (node.getChildren() == null) return false;
        for (UINode child : node.getChildren()) {
            if (hrefOf(child, ctx) != null || hasLinkDescendant(child, ctx)) return true;
        }
        return false;
    }

    private String resolveHref(UINode node, LinkContext ctx) {
        if (ctx == null || node.getLinkTargetId() == null) return null;
        String target = ctx.fileByNodeId().get(node.getLinkTargetId());
        if (target == null || target.equals(ctx.currentFile())) return null;
        return target;
    }

    private void indexNodes(UINode node, String filename, Map<String, String> fileByNodeId) {
        if (node == null) return;
        if (node.getId() != null) fileByNodeId.put(node.getId(), filename);
        if (node.getChildren() != null) {
            for (UINode child : node.getChildren()) indexNodes(child, filename, fileByNodeId);
        }
    }

    private Map<String, TransitionSpec> collectIncomingTransitions(
            Map<String, UINode> rootByFile, Map<String, String> fileByNodeId) {
        Map<String, Map<TransitionSpec, Integer>> counts = new HashMap<>();
        for (Map.Entry<String, UINode> e : rootByFile.entrySet()) {
            countTransitions(e.getValue(), e.getKey(), fileByNodeId, counts);
        }
        Map<String, TransitionSpec> result = new HashMap<>();
        for (Map.Entry<String, Map<TransitionSpec, Integer>> e : counts.entrySet()) {
            TransitionSpec best = null;
            int bestCount = 0;
            for (Map.Entry<TransitionSpec, Integer> c : e.getValue().entrySet()) {
                if (c.getValue() > bestCount) { best = c.getKey(); bestCount = c.getValue(); } // hoa: lay cai gap truoc
            }
            if (best != null) result.put(e.getKey(), best);
        }
        return result;
    }

    private void countTransitions(UINode node, String sourceFile, Map<String, String> fileByNodeId,
                                  Map<String, Map<TransitionSpec, Integer>> counts) {
        if (node == null) return;
        if (node.getLinkTargetId() != null && !"SCROLL_TO".equals(node.getLinkNavigation())) {
            String target = fileByNodeId.get(node.getLinkTargetId());
            TransitionSpec spec = new TransitionSpec(
                    node.getTransitionType(), node.getTransitionDurationMs(), node.getTransitionEasing());
            if (target != null && !target.equals(sourceFile) && spec.isPresent()) {
                counts.computeIfAbsent(target, k -> new LinkedHashMap<>()).merge(spec, 1, Integer::sum);
            }
        }
        if (node.getChildren() != null) {
            for (UINode child : node.getChildren()) countTransitions(child, sourceFile, fileByNodeId, counts);
        }
    }

    private String anchorId(String nodeId) {
        return "node-" + toClassName(nodeId);
    }

    /** SCROLL_TO: "#node-x" neu cung file, "file.html#node-x" neu khac file. Khong co dich thi null. */
    private String resolveScrollHref(UINode node, LinkContext ctx) {
        if (ctx == null || node.getLinkTargetId() == null) return null;
        String targetFile = ctx.fileByNodeId().get(node.getLinkTargetId());
        if (targetFile == null) return null;
        String anchor = "#" + anchorId(node.getLinkTargetId());
        return targetFile.equals(ctx.currentFile()) ? anchor : targetFile + anchor;
    }

    private record LinkContext(Map<String, String> fileByNodeId,
                               String currentFile,
                               Map<String, TransitionSpec> incomingTransitionByFile) {}

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

    private void appendTransitionAttrs(UINode node, StringBuilder sb) {
        TransitionSpec spec = new TransitionSpec(
                node.getTransitionType(),
                node.getTransitionDurationMs(),
                node.getTransitionEasing());
        TransitionCssMapper.CssTransition css = TransitionCssMapper.toCss(spec);
        if (css == null) return;

        // Giữ type gốc của Figma (DISSOLVE, PUSH, ...) để bước 5 còn biết hướng trượt.
        // Nếu type null thì không ghi attribute.
        if (spec.type() != null) {
            sb.append(" data-transition=\"").append(escape(spec.type())).append("\"");
        }
        sb.append(" data-duration=\"").append(css.durationMs()).append("\"")
                .append(" data-easing=\"").append(escape(css.easing())).append("\"");
    }
}
