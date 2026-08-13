package com.tgnguyen.layoutlybe.dto;

import java.util.List;
import java.util.Map;

/**
 * Ket qua sau khi backend duyet (traverse) cay FigmaNode va tu phan tich cau truc.
 * Day la bang chung "nghien cuu cau truc Document-Canvas-Frame-Node" duoc lam bang code Java
 * that su, thay vi chi hien thi cay o frontend.
 */
public record StructureSummary(
        String rootType,               // luon la "DOCUMENT" neu goi tu /file
        int totalNodes,                // tong so node duyet duoc (ca cay)
        int maxDepth,                  // do sau lon nhat: DOCUMENT=0, CANVAS=1, FRAME=2, con no=3...
        Map<String, Integer> countByType, // vi du {"FRAME": 12, "TEXT": 34, "RECTANGLE": 8, "INSTANCE": 5}
        List<String> canvasNames,      // ten cac trang (Page) - moi CANVAS = 1 page nguoi dung thay trong Figma
        List<AutoLayoutFrame> autoLayoutFrames // cac Frame co dung Auto Layout, ung vien tot nhat de sinh code
) {
    public record AutoLayoutFrame(
            String id,
            String name,
            String layoutMode,   // HORIZONTAL | VERTICAL | GRID
            int childCount
    ) {}
}
