package com.tgnguyen.layoutlybe.model.ir;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

public final class RoleClassifier {
    private static final List<String> BUTTON_KEYWORDS = List.of("button", "btn", "cta");
    private static final List<String> INPUT_KEYWORDS = List.of("input", "textfield", "text field", "text-field", "textbox", "text box");
    private static final List<String> CHECKBOX_KEYWORDS = List.of("checkbox", "check box", "check-box");

    private interface Rule {
        boolean matches(JsonNode node);
        NodeRole role();
    }

    private static final List<Rule> RULES = List.of(
            rule(n -> isComponentLike(n) && nameMatchesAny(n, BUTTON_KEYWORDS), NodeRole.BUTTON),
            // Input/checkbox: dat ten ro rang trong Figma, hoac nhan dien theo hinh dang (o rong co vien)
            rule(n -> !"TEXT".equals(type(n)) && nameMatchesAny(n, CHECKBOX_KEYWORDS), NodeRole.CHECKBOX),
            rule(n -> !"TEXT".equals(type(n)) && nameMatchesAny(n, INPUT_KEYWORDS) && countTextNodes(n) <= 1, NodeRole.INPUT),
            rule(RoleClassifier::looksLikeCheckbox, NodeRole.CHECKBOX),
            rule(RoleClassifier::looksLikeTextField, NodeRole.INPUT),
            rule(n -> "TEXT".equals(type(n)), NodeRole.TEXT)
            // sau này thêm LINK... ở đây
    );

    private RoleClassifier() {}

    public static NodeRole classify(JsonNode node) {
        for (Rule rule : RULES) {
            if (rule.matches(node)) return rule.role();
        }
        return NodeRole.CONTAINER; // mặc định = hành vi cũ (div)
    }

    private static Rule rule(Predicate<JsonNode> predicate, NodeRole role) {
        return new Rule() {
            public boolean matches(JsonNode node) { return predicate.test(node); }
            public NodeRole role() { return role; }
        };
    }

    /** O nhap lieu: khung RONG (khong con) co vien, dang chu nhat ngang (vd 320x56). Khung co text ben trong = button/card, khong khop. */
    private static boolean looksLikeTextField(JsonNode node) {
        if (!isBoxType(node) || hasChildren(node) || !hasVisibleStroke(node)) return false;
        double w = boundsOf(node, "width"), h = boundsOf(node, "height");
        return h >= 28 && h <= 96 && w >= h * 2.5;
    }

    /** Checkbox: o vuong nho, khong con, co vien. */
    private static boolean looksLikeCheckbox(JsonNode node) {
        if (!isBoxType(node) || hasChildren(node) || !hasVisibleStroke(node)) return false;
        double w = boundsOf(node, "width"), h = boundsOf(node, "height");
        return w >= 12 && w <= 32 && h >= 12 && h <= 32 && Math.abs(w - h) <= 2;
    }

    private static boolean isBoxType(JsonNode node) {
        String t = type(node);
        return "RECTANGLE".equals(t) || "FRAME".equals(t) || "COMPONENT".equals(t) || "INSTANCE".equals(t);
    }

    private static boolean hasChildren(JsonNode node) {
        JsonNode children = node.get("children");
        return children != null && children.isArray() && children.size() > 0;
    }

    private static boolean hasVisibleStroke(JsonNode node) {
        JsonNode strokes = node.get("strokes");
        if (strokes == null || !strokes.isArray()) return false;
        for (JsonNode s : strokes) {
            JsonNode visible = s.get("visible");
            if (visible == null || visible.asBoolean(true)) return true;
        }
        return false;
    }

    private static double boundsOf(JsonNode node, String field) {
        JsonNode box = node.get("absoluteBoundingBox");
        JsonNode v = box != null ? box.get(field) : null;
        return v != null && v.isNumber() ? v.asDouble() : 0;
    }

    private static int countTextNodes(JsonNode node) {
        int n = "TEXT".equals(type(node)) ? 1 : 0;
        JsonNode children = node.get("children");
        if (children != null && children.isArray()) for (JsonNode c : children) n += countTextNodes(c);
        return n;
    }

    private static boolean isComponentLike(JsonNode node) {
        String t = type(node);
        return "COMPONENT".equals(t) || "INSTANCE".equals(t);
    }

    private static boolean nameMatchesAny(JsonNode node, List<String> keywords) {
        String name = textOf(node, "name");
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return keywords.stream().anyMatch(lower::contains);
    }

    private static String type(JsonNode node) { return textOf(node, "type"); }

    private static String textOf(JsonNode node, String field) {
        if (node == null || node.isNull()) return null;
        JsonNode value = node.get(field);
        return value != null && !value.isNull() ? value.asText() : null;
    }
}