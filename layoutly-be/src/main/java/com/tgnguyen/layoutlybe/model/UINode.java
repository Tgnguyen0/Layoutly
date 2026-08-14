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
    private List<UINode> children = new ArrayList<>();
}
