package com.tgnguyen.layoutlybe.model.ir;

public record EdgeInsets(Double top, Double right, Double bottom, Double left) {
    public static EdgeInsets empty() {
        return new EdgeInsets(null, null, null, null);
    }
}
