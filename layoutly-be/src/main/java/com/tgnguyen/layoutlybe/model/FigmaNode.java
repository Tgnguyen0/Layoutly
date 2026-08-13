package com.tgnguyen.layoutlybe.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Dai dien 1 node bat ky trong cay Figma: DOCUMENT -> CANVAS -> FRAME -> ... -> TEXT/RECTANGLE/VECTOR
 * Dung 1 record duy nhat cho moi loai node (thay vi sealed interface rieng tung type)
 * de don gian hoa giai doan "nghien cuu cau truc" nay. Field nao JSON khong co se tu la null,
 * KHONG duoc doc field cua 1 type ma chua kiem tra "type" truoc (vi du: doc characters() tren
 * 1 node FRAME se luon tra ve null, khong loi, nhung cung khong co y nghia).
 *
 * ignoreUnknown = true vi Figma response co rat nhieu field khac (scrollBehavior, blendMode,
 * constraints, reactions...) khong can thiet cho viec sinh code, bo qua het.
 */
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
        Integer itemSpacing,               // -> gap
        Integer paddingLeft,
        Integer paddingRight,
        Integer paddingTop,
        Integer paddingBottom,

        // ----- Rieng cho hinh dang (FRAME/RECTANGLE/COMPONENT...) -----
        Integer cornerRadius,
        List<Fill> fills,
        List<Stroke> strokes,
        BoundingBox absoluteBoundingBox,

        // ----- Rieng cho TEXT -----
        String characters,
        TextStyle style,

        // ----- Rieng cho INSTANCE (component da dat variant) -----
        java.util.Map<String, ComponentProperty> componentProperties
) {

    /** true neu node nay con the chua node con (khong phai leaf node nhu TEXT/VECTOR/RECTANGLE don gian) */
    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BoundingBox(double x, double y, double width, double height) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Fill(String type, Color color) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Stroke(String type, Color color) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Color(double r, double g, double b, double a) {
        /** Convert Figma color (0..1 float) sang hex CSS, vi du #3B82F6 */
        public String toHex() {
            int red = (int) Math.round(r * 255);
            int green = (int) Math.round(g * 255);
            int blue = (int) Math.round(b * 255);
            return String.format("#%02X%02X%02X", red, green, blue);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TextStyle(
            String fontFamily,
            Double fontSize,
            Double fontWeight,
            Double lineHeightPx,
            Double letterSpacing
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ComponentProperty(String type, String value) {}
}
