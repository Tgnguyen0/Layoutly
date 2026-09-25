package com.tgnguyen.layoutlybe.model.ir;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StyleSpec {
    String backgroundColor;
    String borderColor;
    Double borderWidth;
    Double opacity;
    Double cornerRadius;
    String fontFamily;
    Double fontSize;
    Double fontWeight;
    Double lineHeight;
    Double letterSpacing;
}
