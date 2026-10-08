package com.tgnguyen.layoutlybe.service;

import java.text.Normalizer;
import java.util.Locale;

/** Suy ra thuoc tinh <input> (type, name) tu ten node, placeholder va label ben canh. Dung chung cho HTML + React. */
final class InputSupport {
    private InputSupport() {}

    static String inferType(String... hints) {
        StringBuilder sb = new StringBuilder();
        for (String h : hints) if (h != null) sb.append(h.toLowerCase(Locale.ROOT)).append(' ');
        String t = sb.toString();
        if (t.contains("password") || t.contains("mật khẩu") || t.contains("mat khau")) return "password";
        // "Username or email address" nhan ca username => khong ep type=email
        if (t.contains("username") || t.contains("user name")) return "text";
        if (t.contains("email") || t.contains("e-mail")) return "email";
        if (t.contains("search") || t.contains("tìm kiếm")) return "search";
        if (t.contains("phone") || t.contains("tel")) return "tel";
        return "text";
    }

    /** "Username or email address" -> "username-or-email-address"; null neu khong co gi de dat ten. */
    static String fieldName(String label, String nodeName) {
        String base = label != null && !label.isBlank() ? label : nodeName;
        if (base == null) return null;
        String slug = Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return slug.isEmpty() ? null : slug;
    }
}
