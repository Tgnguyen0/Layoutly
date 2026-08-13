package com.tgnguyen.layoutlybe.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Service
public class FigmaService {

    private final WebClient figmaWebClient;

    @Value("${figma.api.token}")
    private String figmaToken;

    public FigmaService(WebClient figmaWebClient) {
        this.figmaWebClient = figmaWebClient;
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
        String tokenToUse = (tokenOverride != null && !tokenOverride.isBlank())
                ? tokenOverride
                : figmaToken;

        if (tokenToUse == null || tokenToUse.isBlank()) {
            return Mono.error(new IllegalStateException(
                    "Chua co Figma token. Nhap token vao trang test, hoac set bien moi truong FIGMA_TOKEN."));
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
