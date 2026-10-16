package com.tgnguyen.layoutlybe.service;

import com.tgnguyen.layoutlybe.model.ir.Bounds;
import com.tgnguyen.layoutlybe.model.ir.DesignNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Select complete screens before considering their nested layout sections. */
public final class DesignRootSelector {
    private DesignRootSelector() {}

    public static DesignNode defaultPage(DesignNode root) {
        if (root != null && "DOCUMENT".equals(root.getType())) {
            return children(root).stream().filter(n -> "CANVAS".equals(n.getType()) && !children(n).isEmpty())
                    .findFirst().orElse(root);
        }
        return root;
    }

    public static DesignNode preview(DesignNode root, double targetWidth) {
        DesignNode page = defaultPage(root);
        if (page == null) return null;
        if (continuousSections(page)) return page;
        List<DesignNode> screens = new ArrayList<>();
        List<DesignNode> fallback = new ArrayList<>();
        collect(page, screens, fallback);
        return (screens.isEmpty() ? fallback : screens).stream()
                .max(Comparator.comparingDouble(n -> score(n, targetWidth))).orElse(page);
    }

    /** Matches HTML AUTO's existing page policy; used by opt-in React page consistency. */
    public static List<DesignNode> exportPages(DesignNode root) {
        if (root == null) return List.of();
        if (!"DOCUMENT".equals(root.getType()) && !"CANVAS".equals(root.getType())) return List.of(root);
        List<DesignNode> result = new ArrayList<>();
        List<DesignNode> canvases = "CANVAS".equals(root.getType()) ? List.of(root) : children(root);
        for (DesignNode canvas : canvases) {
            if (!"CANVAS".equals(canvas.getType())) continue;
            List<DesignNode> frames = children(canvas).stream().filter(n -> "FRAME".equals(n.getType())).toList();
            if (frames.size() <= 1 || continuousSections(canvas)) result.add(canvas);
            else result.addAll(frames);
        }
        return result;
    }

    /** Adjacent equal-width sections on the same canvas form one long page. */
    public static boolean continuousSections(DesignNode page) {
        if (page == null || !"CANVAS".equals(page.getType()) || children(page).size() < 2
                || children(page).stream().anyMatch(n -> !"FRAME".equals(n.getType()))) return false;
        List<DesignNode> sections = children(page).stream().sorted(Comparator.comparingDouble(n ->
                n.getBounds() == null || n.getBounds().y() == null ? Double.MAX_VALUE : n.getBounds().y())).toList();
        Bounds first = sections.get(0).getBounds();
        if (first == null || first.x() == null || first.y() == null || first.width() == null || first.width() < 300) return false;
        boolean shortSection = false;
        double end = first.y();
        for (DesignNode section : sections) {
            Bounds b = section.getBounds();
            if (b == null || b.x() == null || b.y() == null || b.width() == null || b.height() == null
                    || Math.abs(b.x() - first.x()) > 2 || Math.abs(b.width() - first.width()) > 2
                    || Math.abs(b.y() - end) > 2 || b.height() <= 0) return false;
            shortSection |= b.height() < b.width() * .3;
            end = b.y() + b.height();
        }
        return shortSection;
    }

    private static void collect(DesignNode node, List<DesignNode> screens, List<DesignNode> fallback) {
        Bounds bounds = node.getBounds();
        if ("FRAME".equals(node.getType()) && bounds != null && bounds.width() != null
                && bounds.height() != null && bounds.width() > 0 && bounds.height() > 0) {
            fallback.add(node);
            if (bounds.width() >= 300 && bounds.height() >= 500 && !presentation(node)) {
                screens.add(node);
                // A nearer viewport width on a nested Content frame must not replace its page.
                return;
            }
        }
        for (DesignNode child : children(node)) collect(child, screens, fallback);
    }

    private static boolean presentation(DesignNode node) {
        String name = node.getName() == null ? "" : node.getName().toLowerCase(Locale.ROOT);
        return name.matches(".*(cover|thumbnail|component|style|guide|presentation|mockup|dribbble|open.graph|favicon|webclip).*" );
    }

    private static double score(DesignNode node, double targetWidth) {
        double width = node.getBounds().width(), height = node.getBounds().height();
        String name = node.getName() == null ? "" : node.getName().toLowerCase(Locale.ROOT);
        double score = -Math.abs(width - targetWidth) * 20 + Math.min(height, 8000);
        if (height >= width * .6 && height <= width * 8) score += 5000;
        if (name.matches(".*(desktop|mobile|landing|homepage|home|website|web).*")) score += 8000;
        if (presentation(node)) score -= 50000;
        return score;
    }

    private static List<DesignNode> children(DesignNode node) {
        return node == null || node.getChildren() == null ? List.of() : node.getChildren();
    }
}
