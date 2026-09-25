package com.tgnguyen.layoutlybe.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;


@JsonIgnoreProperties(ignoreUnknown = true)
public record FigmaNode(
        String id,
        String name,
        String type,               // DOCUMENT | CANVAS | FRAME | GROUP | COMPONENT | INSTANCE | TEXT | RECTANGLE | VECTOR | ...

        // ----- Rieng cho node co the chua node con (DOCUMENT, CANVAS, FRAME, GROUP...) -----
        List<FigmaNode> children,

        // ----- Rieng cho Auto Layout (FRAME/COMPONENT/INSTANCE co layoutMode != NONE) -----
        String layoutMode,                 // HORIZONTAL | VERTICAL | GRID | NONE
        String primaryAxisAlignItems,      // -> justify-content
        String counterAxisAlignItems,      // -> align-items
        Double itemSpacing,               // -> gap
        Double paddingLeft,
        Double paddingRight,
        Double paddingTop,
        Double paddingBottom,
        String layoutPositioning,         // AUTO | ABSOLUTE
        String layoutSizingHorizontal,    // FIXED | HUG | FILL
        String layoutSizingVertical,      // FIXED | HUG | FILL
        Boolean clipsContent,

        // ----- Rieng cho hinh dang (FRAME/RECTANGLE/COMPONENT...) -----
        Double cornerRadius,
        Double opacity,
        Boolean visible,
        Double strokeWeight,
        List<Fill> fills,
        List<Stroke> strokes,
        List<Effect> effects,
        BoundingBox absoluteBoundingBox,

        // ----- Rieng cho TEXT -----
        String characters,
        TextStyle style,

        // ----- Rieng cho INSTANCE (component da dat variant) -----
        String componentId,
        String componentSetId,
        Map<String, ComponentProperty> componentProperties
) {

    /** true neu node nay con the chua node con (khong phai leaf node nhu TEXT/VECTOR/RECTANGLE don gian) */
    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BoundingBox(double x, double y, double width, double height) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Fill(
            String type,
            Color color,
            Double opacity,
            Boolean visible,
            String imageRef,
            String scaleMode
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Stroke(
            String type,
            Color color,
            Double opacity,
            Boolean visible
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Effect(
            String type,
            Boolean visible,
            Double radius,
            Double spread,
            Vector offset,
            Color color
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Vector(double x, double y) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Color(double r, double g, double b, double a) {
        /** Convert Figma color (0..1 float) sang hex CSS, vi du #3B82F6 */
        public String toHex() {
            int red = channelToByte(r);
            int green = channelToByte(g);
            int blue = channelToByte(b);
            return String.format("#%02X%02X%02X", red, green, blue);
        }

        private static int channelToByte(double channel) {
            return (int) Math.round(Math.max(0, Math.min(1, channel)) * 255);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TextStyle(
            String fontFamily,
            Double fontSize,
            Double fontWeight,
            Double lineHeightPx,
            Double letterSpacing,
            String textAlignHorizontal,
            String textAlignVertical,
            String textCase,
            String textDecoration
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ComponentProperty(String type, String value) {}
}
