package com.tgnguyen.layoutlybe.model.ir;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SizingSpec {
    @Builder.Default
    SizingMode horizontal = SizingMode.UNKNOWN;
    @Builder.Default
    SizingMode vertical = SizingMode.UNKNOWN;
    Double width;
    Double height;
    Double minWidth;
    Double maxWidth;
    Double minHeight;
    Double maxHeight;
}
