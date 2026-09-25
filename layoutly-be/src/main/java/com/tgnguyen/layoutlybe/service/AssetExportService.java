package com.tgnguyen.layoutlybe.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgnguyen.layoutlybe.model.UINode;
import com.tgnguyen.layoutlybe.model.ir.DesignNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AssetExportService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AssetExportService.class);
    private static final int MAX_IDS_PER_REQUEST = 100;
    private static final int DOWNLOAD_CONCURRENCY = 6;
    private static final Duration ASSET_DOWNLOAD_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration FIGMA_RENDER_TIMEOUT = Duration.ofMinutes(2);

    private final FigmaService figmaService;
    private final WebClient imageClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AssetExportService(FigmaService figmaService) {
        this.figmaService = figmaService;
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(20 * 1024 * 1024))
                .build();
        this.imageClient = WebClient.builder().exchangeStrategies(strategies).build();
    }

    public Mono<AssetBundle> exportAssets(String fileKey, String token, UINode root) {
        List<ImageNodeRef> imageNodes = new ArrayList<>();
        collectImageNodes(root, imageNodes);

        return exportAssets(fileKey, token, imageNodes);
    }

    public Mono<AssetBundle> exportAssets(String fileKey, String token, DesignNode root) {
        List<ImageNodeRef> imageNodes = new ArrayList<>();
        collectImageNodes(root, imageNodes);
        return exportAssets(fileKey, token, imageNodes);
    }

    private Mono<AssetBundle> exportAssets(String fileKey, String token, List<ImageNodeRef> imageNodes) {

        if (imageNodes.isEmpty()) {
            return Mono.just(new AssetBundle(Map.of(), Map.of()));
        }

        return fetchImageUrls(fileKey, token, imageNodes)
                .flatMap(imageUrls -> {
                    List<AssetDownload> downloads = new ArrayList<>();
                    for (ImageNodeRef node : imageNodes) {
                        String url = imageUrls.get(node.id());
                        if (url == null || url.isBlank()) continue;

                        String assetPath = "assets/" + sanitizeId(node.id()) + ".png";
                        downloads.add(new AssetDownload(node.id(), assetPath, url));
                    }

                    return Flux.fromIterable(downloads)
                            .flatMap(this::downloadAsset, DOWNLOAD_CONCURRENCY)
                            .collectList()
                            .map(this::toBundle);
                });
    }

    private Mono<Map<String, String>> fetchImageUrls(String fileKey, String token,
                                                       List<ImageNodeRef> imageNodes) {
        List<String> ids = new ArrayList<>(new LinkedHashSet<>(
                imageNodes.stream().map(ImageNodeRef::id).toList()));

        return Flux.range(0, (ids.size() + MAX_IDS_PER_REQUEST - 1) / MAX_IDS_PER_REQUEST)
                .concatMap(batchIndex -> {
                    int fromIndex = batchIndex * MAX_IDS_PER_REQUEST;
                    int toIndex = Math.min(fromIndex + MAX_IDS_PER_REQUEST, ids.size());
                    String batchIds = String.join(",", ids.subList(fromIndex, toIndex));
                    return figmaService.getImages(fileKey, batchIds, "png", token)
                            .timeout(FIGMA_RENDER_TIMEOUT)
                            .map(this::parseImageUrls);
                })
                .collect(LinkedHashMap::new, Map::putAll);
    }

    private Map<String, String> parseImageUrls(String rawJson) {
        try {
            JsonNode images = objectMapper.readTree(rawJson).get("images");
            if (images == null || !images.isObject()) return Map.of();

            Map<String, String> urls = new LinkedHashMap<>();
            images.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value != null && !value.isNull() && !value.asText().isBlank()) {
                    urls.put(entry.getKey(), value.asText());
                }
            });
            return urls;
        } catch (Exception exception) {
            throw new RuntimeException("Loi khi doc URL asset Figma: " + exception.getMessage(), exception);
        }
    }

    private Mono<DownloadedAsset> downloadAsset(AssetDownload asset) {
        return imageClient.get()
                .uri(asset.url())
                .retrieve()
                .bodyToMono(byte[].class)
                .timeout(ASSET_DOWNLOAD_TIMEOUT)
                .map(bytes -> new DownloadedAsset(asset.nodeId(), asset.path(), bytes))
                .onErrorResume(exception -> {
                    LOGGER.warn("Skipping Figma asset {}: {}", asset.nodeId(), exception.getMessage());
                    return Mono.empty();
                });
    }

    private AssetBundle toBundle(List<DownloadedAsset> assets) {
        Map<String, String> cssUrlByNodeId = new LinkedHashMap<>();
        Map<String, byte[]> zipAssetByPath = new LinkedHashMap<>();
        for (DownloadedAsset asset : assets) {
            cssUrlByNodeId.put(asset.nodeId(), asset.path());
            zipAssetByPath.put(asset.path(), asset.bytes());
        }
        return new AssetBundle(cssUrlByNodeId, zipAssetByPath);
    }

    public Mono<Map<String, String>> getPreviewImageUrls(String fileKey, String token, UINode root) {
        List<ImageNodeRef> imageNodes = new ArrayList<>();
        collectImageNodes(root, imageNodes);

        return getPreviewImageUrls(fileKey, token, imageNodes);
    }

    public Mono<Map<String, String>> getPreviewImageUrls(String fileKey, String token, DesignNode root) {
        List<ImageNodeRef> imageNodes = new ArrayList<>();
        collectImageNodes(root, imageNodes);
        return getPreviewImageUrls(fileKey, token, imageNodes);
    }

    private Mono<Map<String, String>> getPreviewImageUrls(String fileKey, String token,
                                                           List<ImageNodeRef> imageNodes) {

        if (imageNodes.isEmpty()) return Mono.just(Map.of());

        return fetchImageUrls(fileKey, token, imageNodes);
    }

    private void collectImageNodes(UINode node, List<ImageNodeRef> result) {
        if (node == null) return;
        if (node.isExportAsImage()) result.add(new ImageNodeRef(node.getId()));
        for (UINode child : node.getChildren()) {
            collectImageNodes(child, result);
        }
    }

    private void collectImageNodes(DesignNode node, List<ImageNodeRef> result) {
        if (node == null) return;
        if (node.getAsset() != null && node.getAsset().exportAsImage()) {
            result.add(new ImageNodeRef(node.getId()));
        }
        if (node.getChildren() != null) {
            for (DesignNode child : node.getChildren()) {
                collectImageNodes(child, result);
            }
        }
    }

    private String sanitizeId(String id) {
        if (id == null || id.isBlank()) return "asset";
        return id.replaceAll("[^a-zA-Z0-9_-]+", "-").replaceAll("(^-|-$)", "");
    }

    public record AssetBundle(Map<String, String> cssUrlByNodeId, Map<String, byte[]> zipAssetByPath) {
    }

    private record ImageNodeRef(String id) {
    }

    private record AssetDownload(String nodeId, String path, String url) {
    }

    private record DownloadedAsset(String nodeId, String path, byte[] bytes) {
    }
}
