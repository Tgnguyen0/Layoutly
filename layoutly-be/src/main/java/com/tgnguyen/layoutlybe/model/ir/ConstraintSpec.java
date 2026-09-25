package com.tgnguyen.layoutlybe.model.ir;

public record ConstraintSpec(String horizontal, String vertical) {
    public static ConstraintSpec empty() {
        return new ConstraintSpec(null, null);
    }
}
