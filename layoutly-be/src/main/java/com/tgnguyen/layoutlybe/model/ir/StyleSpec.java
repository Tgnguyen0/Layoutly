package com.tgnguyen.layoutlybe.model.ir;

import lombok.Builder;
import lombok.Value;

import java.util.List;

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
    String textAlign;
    String textAutoResize;
    String fontStyle;
    List<Double> cornerRadii;
    List<ShadowSpec> shadows;
    Boolean clipsContent;
    Boolean visible;
}
