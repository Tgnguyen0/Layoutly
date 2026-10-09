package com.tgnguyen.layoutlybe.model.ir;

public record TransitionSpec(String type, Integer durationMs, String easing) {

    public static TransitionSpec none() {
        return new TransitionSpec(null, null, null);
    }

    public boolean isPresent() {
        return type != null || durationMs != null || easing != null;
    }
}