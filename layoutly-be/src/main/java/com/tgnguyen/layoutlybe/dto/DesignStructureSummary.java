package com.tgnguyen.layoutlybe.dto;

import java.util.Map;

public record DesignStructureSummary(
        int canvasCount,
        int frameCount,
        int nodeCount,
        int maxDepth,
        int autoLayoutCount,
        int flexCount,
        int gridCount,
        int absoluteCount,
        int imageCount,
        int vectorCount,
        Map<String, Integer> countByType
) {
}
