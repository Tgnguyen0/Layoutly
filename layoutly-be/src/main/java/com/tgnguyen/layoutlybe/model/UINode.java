package com.tgnguyen.layoutlybe.model;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

// Dai dien cho 1 node trong cay UI da duoc chuan hoa, thay the cho JSON tho cua Figma.
// Cac tuan sau (trich xuat thuoc tinh, sinh HTML/CSS) se lam viec truc tiep tren cay nay.
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class UINode {
    private String id;
    private String name;
    private String type; // FRAME, GROUP, TEXT, RECTANGLE, COMPONENT, INSTANCE, VECTOR...
    private String characters;
    private List<UINode> children = new ArrayList<>();

    // Vi tri va kich thuoc tuyet doi (tu absoluteBoundingBox)
    private Double x;
    private Double y;
    private Double width;
    private Double height;

    // Mau nen - dang chuoi CSS san sang dung, vd "rgba(232, 163, 61, 1.00)"
    private String backgroundColor;

    // Do trong 0.0 - 1.0
    private Double opacity;

    // Bo goc
    private Double cornerRadius;

    // Vien
    private String borderColor;
    private Double borderWidth;

    // Font chu - chi co gia tri voi node type = TEXT
    private String fontFamily;
    private Double fontSize;
    private Double fontWeight;
    private Double lineHeight;
    private Double letterSpacing;

    // Khoang cach ben trong / giua cac phan tu con (Auto Layout)
    private Double paddingTop;
    private Double paddingRight;
    private Double paddingBottom;
    private Double paddingLeft;
    private Double itemSpacing;
}
