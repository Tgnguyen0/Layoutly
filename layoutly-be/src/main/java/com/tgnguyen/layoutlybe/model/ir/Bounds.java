package com.tgnguyen.layoutlybe.model.ir;

public record Bounds(Double x, Double y, Double width, Double height) {
    public static Bounds empty() {
        return new Bounds(null, null, null, null);
    }
}
