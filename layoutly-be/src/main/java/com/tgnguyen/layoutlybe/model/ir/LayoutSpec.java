package com.tgnguyen.layoutlybe.model.ir;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LayoutSpec {
    @Builder.Default
    LayoutType type = LayoutType.NONE;
    @Builder.Default
    Direction direction = Direction.NONE;
    Double gap;
    @Builder.Default
    EdgeInsets padding = EdgeInsets.empty();
    @Builder.Default
    Alignment mainAxisAlignment = Alignment.START;
    @Builder.Default
    Alignment crossAxisAlignment = Alignment.START;
    boolean wrap;
    @Builder.Default
    Positioning positioning = Positioning.AUTO;
    @Builder.Default
    ConstraintSpec constraints = ConstraintSpec.empty();
}
