package com.tgnguyen.layoutlybe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgnguyen.layoutlybe.dto.StructureSummary;
import com.tgnguyen.layoutlybe.exception.FigmaRateLimitException;
import com.tgnguyen.layoutlybe.model.FigmaFileResponse;
import com.tgnguyen.layoutlybe.model.FigmaNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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
    @Value("${figma.api.min-interval-ms:6500}")
    private long minIntervalMs;
    @Value("${layoutly.cache.directory:.layoutly-cache}")
    private String cacheDirectory;

    private final Map<String, Long> rateLimitedUntilByToken = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong nextRealCallAt = new java.util.concurrent.atomic.AtomicLong(0);

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
        return getFileParsed(fileKey, tokenOverride).map(this::analyzeStructure);
    }

    public StructureSummary analyzeStructureJson(String rawJson) {
        try {
            return analyzeStructure(objectMapper.readValue(rawJson, FigmaFileResponse.class));
        } catch (Exception exception) {
            throw new RuntimeException("Loi parse JSON khi phan tich structure: " + exception.getMessage(), exception);
        }
    }

    private StructureSummary analyzeStructure(FigmaFileResponse file) {
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
        String cacheKey = "file:" + fileKey;
        CachedFile memoryCached = responseCache.get(cacheKey);
        if (memoryCached != null) {
            writePersistentFile(fileKey, memoryCached.json());
            return Mono.just(memoryCached.json());
        }

        Optional<String> diskCached = readPersistentFile(fileKey);
        if (diskCached.isPresent()) {
            responseCache.put(cacheKey, new CachedFile(diskCached.get(), System.currentTimeMillis()));
            return Mono.just(diskCached.get());
        }

        return callFigma("/files/" + fileKey, tokenOverride)
                .doOnNext(json -> cacheFileResponse(fileKey, json));
    }

    /** Import/refresh path: bypasses the old short-lived response cache. */
    public Mono<String> fetchFileFresh(String fileKey, String tokenOverride) {
        return callFigma("/files/" + fileKey, tokenOverride)
                .doOnNext(json -> cacheFileResponse(fileKey, json));
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
        // Real calls are serialized below; cache hits never enter this queue.
        return scheduledCall(path, tokenToUse, 0);
    }

    private Mono<String> scheduledCall(String path, String token, int retryCount) {
        return Mono.defer(() -> {
            long remainingSeconds = remainingCooldownSeconds(token);
            if (remainingSeconds > 0) {
                return Mono.error(new FigmaRateLimitException(remainingSeconds));
            }

            long waitMs = reserveRequestSlot();
            return Mono.delay(Duration.ofMillis(waitMs))
                    .then(doCallFigma(path, token))
                    .onErrorResume(FigmaRateLimitException.class, exception -> {
                        if (retryCount >= 2 || exception.getRetryAfterSeconds() > 30) {
                            return Mono.error(exception);
                        }
                        long retryDelay = Math.max(1, exception.getRetryAfterSeconds());
                        return Mono.delay(Duration.ofSeconds(retryDelay))
                                .then(scheduledCall(path, token, retryCount + 1));
                    });
        });
    }

    private long reserveRequestSlot() {
        long interval = Math.max(0, minIntervalMs);
        while (true) {
            long now = System.currentTimeMillis();
            long current = nextRealCallAt.get();
            long scheduledAt = Math.max(now, current);
            if (nextRealCallAt.compareAndSet(current, scheduledAt + interval)) {
                return Math.max(0, scheduledAt - now);
            }
        }
    }

    private long remainingCooldownSeconds(String token) {
        long until = rateLimitedUntilByToken.getOrDefault(token, 0L);
        long remainingMs = until - System.currentTimeMillis();
        if (remainingMs <= 0) {
            rateLimitedUntilByToken.remove(token, until);
            return 0;
        }
        return Math.max(1, (remainingMs + 999) / 1000);
    }

    private Mono<String> doCallFigma(String path, String tokenToUse) {
        return figmaWebClient.get()
                .uri(path)
                .header("X-Figma-Token", tokenToUse)
                .retrieve()
                .onStatus(status -> status.value() == 429, response -> {
                    long retryAfterSeconds = parseRetryAfter(
                            response.headers().asHttpHeaders().getFirst("Retry-After"));
                    String planTier = response.headers().asHttpHeaders().getFirst("X-Figma-Plan-Tier");
                    String rateLimitType = response.headers().asHttpHeaders().getFirst("X-Figma-Rate-Limit-Type");
                    String upgradeUrl = response.headers().asHttpHeaders().getFirst("X-Figma-Upgrade-Link");
                    rateLimitedUntilByToken.put(
                            tokenToUse,
                            System.currentTimeMillis() + Duration.ofSeconds(retryAfterSeconds).toMillis());
                    return response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(ignored -> Mono.error(new FigmaRateLimitException(
                                    retryAfterSeconds, planTier, rateLimitType, upgradeUrl)));
                })
                .onStatus(status -> status.isError(), response -> {
                    return response.bodyToMono(String.class)
                            .flatMap(body -> {
                                return Mono.error(new WebClientResponseException(
                                        response.statusCode().value(),
                                        "Figma API loi: " + body,
                                        response.headers().asHttpHeaders(), null, null));
                            });
                })
                .bodyToMono(String.class);
    }

    private void cacheFileResponse(String fileKey, String json) {
        responseCache.put("file:" + fileKey, new CachedFile(json, System.currentTimeMillis()));
        writePersistentFile(fileKey, json);
    }

    private Optional<String> readPersistentFile(String fileKey) {
        Path cacheFile = cacheFile(fileKey);
        if (cacheFile == null || !Files.isRegularFile(cacheFile)) return Optional.empty();
        try {
            return Optional.of(Files.readString(cacheFile, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private void writePersistentFile(String fileKey, String json) {
        Path cacheFile = cacheFile(fileKey);
        if (cacheFile == null) return;
        try {
            Files.createDirectories(cacheFile.getParent());
            Path temporary = cacheFile.resolveSibling(cacheFile.getFileName() + ".tmp");
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, cacheFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignoredAtomicMove) {
                Files.move(temporary, cacheFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignoredCacheFailure) {
            // Disk cache is an optimization; a write failure must not break conversion.
        }
    }

    private Path cacheFile(String fileKey) {
        if (fileKey == null || !fileKey.matches("[a-zA-Z0-9_-]+")) return null;
        Path root = Path.of(cacheDirectory).toAbsolutePath().normalize().resolve("figma-files");
        Path file = root.resolve(fileKey + ".json").normalize();
        return file.startsWith(root) ? file : null;
    }

    private long parseRetryAfter(String value) {
        if (value == null || value.isBlank()) return 60;
        try {
            return Math.max(0, Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            try {
                ZonedDateTime retryAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME);
                return Math.max(0, Duration.between(ZonedDateTime.now(retryAt.getZone()), retryAt).toSeconds());
            } catch (Exception ignoredDate) {
                return 60;
            }
        }
    }
}
