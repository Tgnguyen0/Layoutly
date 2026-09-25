package com.tgnguyen.layoutlybe.service;

import com.tgnguyen.layoutlybe.dto.DesignStructureSummary;
import com.tgnguyen.layoutlybe.model.ir.DesignNode;
import com.tgnguyen.layoutlybe.model.ir.LayoutType;
import com.tgnguyen.layoutlybe.model.ir.Positioning;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DesignStructureService {

    public DesignStructureSummary analyze(DesignNode root) {
        MutableStats stats = new MutableStats();
        walk(root, 0, stats);
        return new DesignStructureSummary(
                stats.canvasCount,
                stats.frameCount,
                stats.nodeCount,
                stats.maxDepth,
                stats.autoLayoutCount,
                stats.flexCount,
                stats.gridCount,
                stats.absoluteCount,
                stats.imageCount,
                stats.vectorCount,
                Map.copyOf(stats.countByType));
    }

    private void walk(DesignNode node, int depth, MutableStats stats) {
        if (node == null) return;
        stats.nodeCount++;
        stats.maxDepth = Math.max(stats.maxDepth, depth);
        String type = node.getType() == null ? "UNKNOWN" : node.getType();
        stats.countByType.merge(type, 1, Integer::sum);
        if ("CANVAS".equals(type)) stats.canvasCount++;
        if ("FRAME".equals(type)) stats.frameCount++;

        LayoutType layoutType = node.getLayout() == null ? LayoutType.NONE : node.getLayout().getType();
        if (layoutType == LayoutType.AUTO_FLEX || layoutType == LayoutType.AUTO_GRID) stats.autoLayoutCount++;
        if (layoutType == LayoutType.AUTO_FLEX) stats.flexCount++;
        if (layoutType == LayoutType.AUTO_GRID) stats.gridCount++;
        if (layoutType == LayoutType.ABSOLUTE
                || (node.getLayout() != null && node.getLayout().getPositioning() == Positioning.ABSOLUTE)) {
            stats.absoluteCount++;
        }

        if (node.getAsset() != null && node.getAsset().exportAsImage()) {
            if ("VECTOR".equals(node.getAsset().kind())) stats.vectorCount++;
            else stats.imageCount++;
        }

        for (DesignNode child : childrenOf(node)) walk(child, depth + 1, stats);
    }

    private List<DesignNode> childrenOf(DesignNode node) {
        return node.getChildren() == null ? List.of() : node.getChildren();
    }

    private static class MutableStats {
        private int canvasCount;
        private int frameCount;
        private int nodeCount;
        private int maxDepth;
        private int autoLayoutCount;
        private int flexCount;
        private int gridCount;
        private int absoluteCount;
        private int imageCount;
        private int vectorCount;
        private final Map<String, Integer> countByType = new LinkedHashMap<>();
    }
}
