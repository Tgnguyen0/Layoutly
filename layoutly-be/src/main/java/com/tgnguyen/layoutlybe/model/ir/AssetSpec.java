package com.tgnguyen.layoutlybe.model.ir;

public record AssetSpec(boolean exportAsImage, String kind, Bounds renderBounds) {
    public AssetSpec(boolean exportAsImage, String kind) {
        this(exportAsImage, kind, null);
    }

    public static AssetSpec none() {
        return new AssetSpec(false, null);
    }
}
