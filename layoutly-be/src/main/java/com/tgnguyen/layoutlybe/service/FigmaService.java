package com.tgnguyen.layoutlybe.service;

import com.tgnguyen.layoutlybe.dto.StructureSummary;
import com.tgnguyen.layoutlybe.model.FigmaFileResponse;
import com.tgnguyen.layoutlybe.model.FigmaNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.ConcurrentHashMap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FigmaService {

    private final WebClient figmaWebClient;

    @Value("${figma.api.token}")
    private String figmaToken;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Cache tong quat theo cache key bat ky (path + tham so) - ap dung cho MOI endpoint
    // Figma, khong chi rieng getFile() nhu truoc. Day la lop phong ve chinh chong khoa
    // tai khoan do goi API lap lai qua nhieu lan trong luc dev/test.
    private final Map<String, CachedFile> responseCache = new ConcurrentHashMap<>();
    // TTL dai hon nhieu so ban dau (1 phut) - uu tien an toan hon la du lieu that moi
    // tung giay trong giai doan dev/test. Co the chinh qua application.properties.
    @Value("${figma.cache.ttl-seconds:300}")
    private long cacheTtlSeconds;

    private record CachedFile(String json, long fetchedAt) {
        boolean isExpired(long ttlMs) {
            return System.currentTimeMillis() - fetchedAt > ttlMs;
        }
    }

    /** Goi co cache: neu cacheKey con han, tra ket qua cu, khong dung Figma. */
    private Mono<String> cachedCall(String cacheKey, Mono<String> realCall) {
        CachedFile cached = responseCache.get(cacheKey);
        long ttlMs = cacheTtlSeconds * 1000;
        if (cached != null && !cached.isExpired(ttlMs)) {
            return Mono.just(cached.json());
        }
        return realCall.doOnNext(json -> responseCache.put(cacheKey, new CachedFile(json, System.currentTimeMillis())));
    }

    public FigmaService(WebClient figmaWebClient) {
        this.figmaWebClient = figmaWebClient;
    }

    /**
     * Lay file Figma va PARSE that su vao FigmaNode (khac voi getFile() tra ve String tho).
     * Day la ham backend "hieu" cau truc, dung cho ham analyzeStructure() ben duoi
     * va sau nay se la dau vao cho ComponentClassifier.
     * dung cache thay the
     */
//    public Mono<FigmaFileResponse> getFileParsed(String fileKey, String tokenOverride) {
//        String tokenToUse = resolveToken(tokenOverride);
//        return figmaWebClient.get()
//                .uri("/files/" + fileKey)
//                .header("X-Figma-Token", tokenToUse)
//                .retrieve()
//                .onStatus(status -> status.isError(), response ->
//                        response.bodyToMono(String.class)
//                                .flatMap(body -> Mono.error(new WebClientResponseException(
//                                        response.statusCode().value(),
//                                        "Figma API loi: " + body,
//                                        null, null, null))))
//                .bodyToMono(FigmaFileResponse.class);
//    }
    public Mono<FigmaFileResponse> getFileParsed(String fileKey, String tokenOverride) {
        // Tai su dung getFile() (co cache) thay vi tu goi Figma rieng -> tranh nhan doi
        // so lan goi API cho cung 1 file, la nguyen nhan chinh gay 429 som hon du kien.
        return getFile(fileKey, tokenOverride)
                .flatMap(json -> {
                    try {
                        return Mono.just(objectMapper.readValue(json, FigmaFileResponse.class));
                    } catch (Exception e) {
                        return Mono.error(new RuntimeException(
                                "Loi parse JSON thanh FigmaFileResponse: " + e.getMessage(), e));
                    }
                });
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
        return cachedCall("file:" + fileKey, callFigma("/files/" + fileKey, tokenOverride));
    }

    /**
     * Lay thong tin cac node cu the trong file (vi du 1 frame/1 component)
     * nodeIds cach nhau boi dau phay, vi du: "1:2,1:3"
     */
    public Mono<String> getFileNodes(String fileKey, String nodeIds, String tokenOverride) {
        return cachedCall("nodes:" + fileKey + ":" + nodeIds,
                callFigma("/files/" + fileKey + "/nodes?ids=" + nodeIds, tokenOverride));
    }

    /**
     * Xuat anh (PNG/SVG/PDF/JPG) cua cac node trong file
     * format: png | svg | pdf | jpg
     * QUAN TRONG: day la endpoint hay bi goi lap lai NHIEU NHAT trong luc test export
     * (moi lan goi /export la 1 lan goi lai cho nay du file khong doi gi) - cache o day
     * la noi giam rui ro khoa tai khoan nhieu nhat.
     */
    public Mono<String> getImages(String fileKey, String nodeIds, String format, String tokenOverride) {
        return cachedCall("images:" + fileKey + ":" + nodeIds + ":" + format,
                callFigma("/images/" + fileKey + "?ids=" + nodeIds + "&format=" + format, tokenOverride));
    }

    /**
     * Lay danh sach components trong file
     */
    public Mono<String> getFileComponents(String fileKey, String tokenOverride) {
        return cachedCall("components:" + fileKey, callFigma("/files/" + fileKey + "/components", tokenOverride));
    }

    /**
     * Lay danh sach styles (color, text, effect styles) trong file
     */
    public Mono<String> getFileStyles(String fileKey, String tokenOverride) {
        return cachedCall("styles:" + fileKey, callFigma("/files/" + fileKey + "/styles", tokenOverride));
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
        // Giu khoang cach toi thieu giua 2 lan goi THAT ra Figma (khong tinh cac lan
        // duoc phuc vu tu cache) - phong truong hop test nhieu file/tham so khac nhau
        // dồn dap, van co the bi he thong chong abuse cua Figma danh dau du moi lan
        // deu la request hop le rieng le. 300ms/request ~ toi da 200 request/phut.
        return Mono.defer(() -> {
            long now = System.currentTimeMillis();
            long last = lastRealCallAt.getAndSet(now);
            long waitMs = Math.max(0, MIN_INTERVAL_MS - (now - last));
            return Mono.delay(java.time.Duration.ofMillis(waitMs)).then(doCallFigma(path, tokenToUse));
        });
    }

    private final java.util.concurrent.atomic.AtomicLong lastRealCallAt = new java.util.concurrent.atomic.AtomicLong(0);
    private static final long MIN_INTERVAL_MS = 300;

    private Mono<String> doCallFigma(String path, String tokenToUse) {
        return figmaWebClient.get()
                .uri(path)
                .header("X-Figma-Token", tokenToUse)
                .retrieve()
                .onStatus(status -> status.isError(), response -> {
                    String retryAfter = response.headers().asHttpHeaders().getFirst("Retry-After");
                    String rateLimitType = response.headers().asHttpHeaders().getFirst("X-Figma-Rate-Limit-Type");

                    return response.bodyToMono(String.class)
                            .flatMap(body -> {
                                String extra = "";
                                if (retryAfter != null) {
                                    try {
                                        long seconds = Long.parseLong(retryAfter);
                                        extra = String.format(" | Retry-After: %d giay (~%.1f gio) | rate-limit-type: %s",
                                                seconds, seconds / 3600.0, rateLimitType);
                                    } catch (NumberFormatException ignored) {
                                        extra = " | Retry-After: " + retryAfter + " | rate-limit-type: " + rateLimitType;
                                    }
                                }
                                return Mono.error(new WebClientResponseException(
                                        response.statusCode().value(),
                                        "Figma API loi: " + body + extra,
                                        null, null, null));
                            });
                })
                .bodyToMono(String.class);
    }
}
