package com.tgnguyen.layoutlybe.model.ir;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder(toBuilder = true)
public class DesignNode {
    String id;
    String name;
    String type;
    String text;
    @Builder.Default
    Bounds bounds = Bounds.empty();
    @Builder.Default
    LayoutSpec layout = LayoutSpec.builder().build();
    @Builder.Default
    SizingSpec sizing = SizingSpec.builder().build();
    @Builder.Default
    StyleSpec style = StyleSpec.builder().build();
    @Builder.Default
    AssetSpec asset = AssetSpec.none();
    @Singular
    List<DesignNode> children;
}
