package com.tgnguyen.layoutlybe.service;

import com.tgnguyen.layoutlybe.model.ir.TransitionSpec;

public final class TransitionCssMapper {
    public static final int DEFAULT_DURATION_MS = 300;
    public static final String DEFAULT_EASING = "ease-out";

    public enum Effect { FADE, SLIDE }

    public record CssTransition(Effect effect, int durationMs, String easing) {}

    private TransitionCssMapper() {
    }

    /** Tra ve null neu node khong co thong tin transition nao. */
    public static CssTransition toCss(TransitionSpec spec) {
        if (spec == null || !spec.isPresent()) return null;
        int duration = spec.durationMs() != null ? Math.max(0, spec.durationMs()) : DEFAULT_DURATION_MS;
        return new CssTransition(effectOf(spec.type()), duration, easingOf(spec.easing()));
    }

    static Effect effectOf(String type) {
        if (type == null) return Effect.FADE;
        return switch (type) {
            case "MOVE_IN", "MOVE_OUT", "PUSH", "SLIDE_IN", "SLIDE_OUT" -> Effect.SLIDE;
            default -> Effect.FADE; // DISSOLVE, SMART_ANIMATE, SCROLL_ANIMATE, null
        };
    }

    static String easingOf(String easing) {
        if (easing == null || easing.isBlank()) return DEFAULT_EASING;
        if (easing.startsWith("cubic-bezier")) return easing; // da doi o FigmaParserService

        return switch (easing) {
            case "LINEAR" -> "linear";
            case "EASE_IN" -> "ease-in";
            case "EASE_OUT" -> "ease-out";
            case "EASE_IN_AND_OUT" -> "ease-in-out";
            case "EASE_IN_BACK" -> "cubic-bezier(0.36, 0, 0.66, -0.56)";
            case "EASE_OUT_BACK" -> "cubic-bezier(0.34, 1.56, 0.64, 1)";
            case "EASE_IN_AND_OUT_BACK" -> "cubic-bezier(0.68, -0.6, 0.32, 1.6)";
            // Spring cua Figma khong co CSS tuong duong chinh xac -> xap xi bang cubic-bezier
            case "GENTLE" -> "cubic-bezier(0.25, 0.8, 0.25, 1)";
            case "QUICK" -> "cubic-bezier(0.2, 0.9, 0.3, 1)";
            case "BOUNCY" -> "cubic-bezier(0.34, 1.56, 0.64, 1)";
            case "SLOW" -> "cubic-bezier(0.4, 0, 0.2, 1)";
            default -> DEFAULT_EASING; // CUSTOM_SPRING hoac gia tri la
        };
    }
}