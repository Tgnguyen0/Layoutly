package com.tgnguyen.layoutlybe.model.ir;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

public final class RoleClassifier {
    private static final List<String> BUTTON_KEYWORDS = List.of("button", "btn", "cta");

    private interface Rule {
        boolean matches(JsonNode node);
        NodeRole role();
    }

    private static final List<Rule> RULES = List.of(
            rule(n -> isComponentLike(n) && nameMatchesAny(n, BUTTON_KEYWORDS), NodeRole.BUTTON),
            rule(n -> "TEXT".equals(type(n)), NodeRole.TEXT)
            // sau này thêm LINK, INPUT... ở đây
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