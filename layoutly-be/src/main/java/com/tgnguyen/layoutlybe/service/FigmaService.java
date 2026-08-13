package com.tgnguyen.layoutlybe.service;

import com.tgnguyen.layoutlybe.dto.StructureSummary;
import com.tgnguyen.layoutlybe.model.FigmaFileResponse;
import com.tgnguyen.layoutlybe.model.FigmaNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FigmaService {

    private final WebClient figmaWebClient;

    @Value("${figma.api.token}")
    private String figmaToken;

    public FigmaService(WebClient figmaWebClient) {
        this.figmaWebClient = figmaWebClient;
    }

    /**
     * Lay file Figma va PARSE that su vao FigmaNode (khac voi getFile() tra ve String tho).
     * Day la ham backend "hieu" cau truc, dung cho ham analyzeStructure() ben duoi
     * va sau nay se la dau vao cho ComponentClassifier.
     */
    public Mono<FigmaFileResponse> getFileParsed(String fileKey, String tokenOverride) {
        String tokenToUse = resolveToken(tokenOverride);
        return figmaWebClient.get()
                .uri("/files/" + fileKey)
                .header("X-Figma-Token", tokenToUse)
                .retrieve()
                .onStatus(status -> status.isError(), response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new WebClientResponseException(
                                        response.statusCode().value(),
                                        "Figma API loi: " + body,
                                        null, null, null))))
                .bodyToMono(FigmaFileResponse.class);
    }

    /**
     * Duyet toan bo cay (Document -> Canvas -> Frame -> ... -> leaf node) va tong hop lai
     * thanh so lieu de "nghien cuu cau truc": dem so node theo type, do sau lon nhat,
     * danh sach ten cac Page (Canvas), va cac Frame dang dung Auto Layout (ung vien
     * tot nhat cho ComponentClassifier o buoc sau).
     */
    public Mono<StructureSummary> analyzeStructure(String fileKey, String tokenOverride) {
        return getFileParsed(fileKey, tokenOverride).map(file -> {
            FigmaNode root = file.document();

            Map<String, Integer> countByType = new LinkedHashMap<>();
            List<String> canvasNames = new ArrayList<>();
            List<StructureSummary.AutoLayoutFrame> autoLayoutFrames = new ArrayList<>();

            int[] totalNodes = {0};
            int[] maxDepth = {0};

            traverse(root, 0, countByType, canvasNames, autoLayoutFrames, totalNodes, maxDepth);

            return new StructureSummary(
                    root != null ? root.type() : null,
                    totalNodes[0],
                    maxDepth[0],
                    countByType,
                    canvasNames,
                    autoLayoutFrames
            );
        });
    }

    /** Duyet de quy 1 lan qua toan bo cay, vua dem vua thu thap thong tin can thiet. */
    private void traverse(FigmaNode node, int depth,
                           Map<String, Integer> countByType,
                           List<String> canvasNames,
                           List<StructureSummary.AutoLayoutFrame> autoLayoutFrames,
                           int[] totalNodes, int[] maxDepth) {
        if (node == null) return;

        totalNodes[0]++;
        maxDepth[0] = Math.max(maxDepth[0], depth);
        countByType.merge(node.type(), 1, Integer::sum);

        if ("CANVAS".equals(node.type())) {
            canvasNames.add(node.name());
        }

        boolean usesAutoLayout = node.layoutMode() != null && !"NONE".equals(node.layoutMode());
        if (usesAutoLayout) {
            int childCount = node.hasChildren() ? node.children().size() : 0;
            autoLayoutFrames.add(new StructureSummary.AutoLayoutFrame(
                    node.id(), node.name(), node.layoutMode(), childCount));
        }

        if (node.hasChildren()) {
            for (FigmaNode child : node.children()) {
                traverse(child, depth + 1, countByType, canvasNames, autoLayoutFrames, totalNodes, maxDepth);
            }
        }
    }

    private String resolveToken(String tokenOverride) {
        String tokenToUse = (tokenOverride != null && !tokenOverride.isBlank())
                ? tokenOverride
                : figmaToken;
        if (tokenToUse == null || tokenToUse.isBlank()) {
            throw new IllegalStateException(
                    "Chua co Figma token. Nhap token vao trang test, hoac set bien moi truong FIGMA_TOKEN.");
        }
        return tokenToUse;
    }

    /**
     * Lay toan bo cau truc file Figma (document tree, pages, frames, layers...)
     * fileKey lay tu URL: figma.com/file/{fileKey}/ten-file
     */
    public Mono<String> getFile(String fileKey, String tokenOverride) {
        return callFigma("/files/" + fileKey, tokenOverride);
    }

    /**
     * Lay thong tin cac node cu the trong file (vi du 1 frame/1 component)
     * nodeIds cach nhau boi dau phay, vi du: "1:2,1:3"
     */
    public Mono<String> getFileNodes(String fileKey, String nodeIds, String tokenOverride) {
        return callFigma("/files/" + fileKey + "/nodes?ids=" + nodeIds, tokenOverride);
    }

    /**
     * Xuat anh (PNG/SVG/PDF/JPG) cua cac node trong file
     * format: png | svg | pdf | jpg
     */
    public Mono<String> getImages(String fileKey, String nodeIds, String format, String tokenOverride) {
        return callFigma("/images/" + fileKey + "?ids=" + nodeIds + "&format=" + format, tokenOverride);
    }

    /**
     * Lay danh sach components trong file
     */
    public Mono<String> getFileComponents(String fileKey, String tokenOverride) {
        return callFigma("/files/" + fileKey + "/components", tokenOverride);
    }

    /**
     * Lay danh sach styles (color, text, effect styles) trong file
     */
    public Mono<String> getFileStyles(String fileKey, String tokenOverride) {
        return callFigma("/files/" + fileKey + "/styles", tokenOverride);
    }

    /**
     * Lay thong tin ve user hien tai gan voi token (test nhanh xem token co hop le khong)
     */
    public Mono<String> getMe(String tokenOverride) {
        return callFigma("/me", tokenOverride);
    }

    private Mono<String> callFigma(String path, String tokenOverride) {
        // Uu tien token nguoi dung nhap truc tiep (header/form) hon token cau hinh san,
        // giup test nhanh nhieu token khac nhau ma khong can restart app
        String tokenToUse;
        try {
            tokenToUse = resolveToken(tokenOverride);
        } catch (IllegalStateException ex) {
            return Mono.error(ex);
        }
        return figmaWebClient.get()
                .uri(path)
                .header("X-Figma-Token", tokenToUse)
                .retrieve()
                .onStatus(status -> status.isError(), response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new WebClientResponseException(
                                        response.statusCode().value(),
                                        "Figma API loi: " + body,
                                        null, null, null))))
                .bodyToMono(String.class);
    }
}
