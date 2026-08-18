package com.tgnguyen.layoutlybe.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgnguyen.layoutlybe.model.UINode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AssetExportService {

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
        List<UINode> imageNodes = new ArrayList<>();
        collectImageNodes(root, imageNodes);

        if (imageNodes.isEmpty()) {
            return Mono.just(new AssetBundle(Map.of(), Map.of()));
        }

        String ids = String.join(",", imageNodes.stream().map(UINode::getId).toList());

        return figmaService.getImages(fileKey, ids, "png", token)
                .flatMap(rawJson -> {
                    try {
                        JsonNode images = objectMapper.readTree(rawJson).get("images");
                        if (images == null || !images.isObject()) {
                            return Mono.just(new AssetBundle(Map.of(), Map.of()));
                        }

                        Map<String, String> cssUrlByNodeId = new LinkedHashMap<>();
                        Map<String, byte[]> zipAssetByPath = new LinkedHashMap<>();
                        List<Mono<Void>> downloads = new ArrayList<>();
                        for (UINode node : imageNodes) {
                            JsonNode urlNode = images.get(node.getId());
                            if (urlNode == null || urlNode.isNull() || urlNode.asText().isBlank()) continue;

                            String url = urlNode.asText();
                            String assetPath = "assets/" + sanitizeId(node.getId()) + ".png";
                            cssUrlByNodeId.put(node.getId(), assetPath);

                            downloads.add(imageClient.get()
                                    .uri(url)
                                    .retrieve()
                                    .bodyToMono(byte[].class)
                                    .doOnNext(bytes -> zipAssetByPath.put(assetPath, bytes))
                                    .then());
                        }

                        return Mono.when(downloads).thenReturn(new AssetBundle(cssUrlByNodeId, zipAssetByPath));
                    } catch (Exception e) {
                        return Mono.error(new RuntimeException("Loi khi xuat asset Figma: " + e.getMessage(), e));
                    }
                });
    }

    public Mono<Map<String, String>> getPreviewImageUrls(String fileKey, String token, UINode root) {
        List<UINode> imageNodes = new ArrayList<>();
        collectImageNodes(root, imageNodes);

        if (imageNodes.isEmpty()) return Mono.just(Map.of());

        String ids = String.join(",", imageNodes.stream().map(UINode::getId).toList());

        return figmaService.getImages(fileKey, ids, "png", token)
                .map(rawJson -> {
                    try {
                        JsonNode images = objectMapper.readTree(rawJson).get("images");
                        if (images == null || !images.isObject()) return Map.of();

                        Map<String, String> urls = new LinkedHashMap<>();
                        for (UINode node : imageNodes) {
                            JsonNode urlNode = images.get(node.getId());
                            if (urlNode != null && !urlNode.isNull() && !urlNode.asText().isBlank()) {
                                urls.put(node.getId(), urlNode.asText());
                            }
                        }
                        return urls;
                    } catch (Exception e) {
                        throw new RuntimeException("Loi khi lay URL preview asset Figma: " + e.getMessage(), e);
                    }
                });
    }

    private void collectImageNodes(UINode node, List<UINode> result) {
        if (node == null) return;
        if (node.isExportAsImage()) result.add(node);
        for (UINode child : node.getChildren()) {
            collectImageNodes(child, result);
        }
    }

    private String sanitizeId(String id) {
        if (id == null || id.isBlank()) return "asset";
        return id.replaceAll("[^a-zA-Z0-9_-]+", "-").replaceAll("(^-|-$)", "");
    }

    public record AssetBundle(Map<String, String> cssUrlByNodeId, Map<String, byte[]> zipAssetByPath) {
    }
}
