package com.tgnguyen.layoutlybe.model.ir;

public record AssetSpec(boolean exportAsImage, String kind) {
    public static AssetSpec none() {
        return new AssetSpec(false, null);
    }
}
