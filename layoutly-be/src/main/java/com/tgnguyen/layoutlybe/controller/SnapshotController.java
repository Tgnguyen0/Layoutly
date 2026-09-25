package com.tgnguyen.layoutlybe.controller;

import com.tgnguyen.layoutlybe.dto.DesignStructureSummary;
import com.tgnguyen.layoutlybe.dto.SnapshotResponse;
import com.tgnguyen.layoutlybe.model.ir.Bounds;
import com.tgnguyen.layoutlybe.model.ir.DesignNode;
import com.tgnguyen.layoutlybe.model.snapshot.FigmaSnapshot;
import com.tgnguyen.layoutlybe.service.AssetExportService;
import com.tgnguyen.layoutlybe.service.CssGeneratorService;
import com.tgnguyen.layoutlybe.service.DesignStructureService;
import com.tgnguyen.layoutlybe.service.FigmaSnapshotService;
import com.tgnguyen.layoutlybe.service.FixtureService;
import com.tgnguyen.layoutlybe.service.HtmlGeneratorService;
import com.tgnguyen.layoutlybe.service.HtmlProjectExportService;
import com.tgnguyen.layoutlybe.service.ReactGeneratorService;
import com.tgnguyen.layoutlybe.service.ReactProjectExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api")
public class SnapshotController {
    private static final String TOKEN_HEADER = "X-Figma-Token";
    private static final Pattern FIGMA_URL_PATTERN = Pattern.compile("/(?:file|design)/([^/?#]+)");

    private final FigmaSnapshotService snapshotService;
    private final DesignStructureService structureService;
    private final HtmlGeneratorService htmlGeneratorService;
    private final CssGeneratorService cssGeneratorService;
    private final ReactGeneratorService reactGeneratorService;
    private final HtmlProjectExportService htmlProjectExportService;
    private final ReactProjectExportService reactProjectExportService;
    private final FixtureService fixtureService;

    public SnapshotController(FigmaSnapshotService snapshotService,
                              DesignStructureService structureService,
                              HtmlGeneratorService htmlGeneratorService,
                              CssGeneratorService cssGeneratorService,
                              ReactGeneratorService reactGeneratorService,
                              HtmlProjectExportService htmlProjectExportService,
                              ReactProjectExportService reactProjectExportService,
                              FixtureService fixtureService) {
        this.snapshotService = snapshotService;
        this.structureService = structureService;
        this.htmlGeneratorService = htmlGeneratorService;
        this.cssGeneratorService = cssGeneratorService;
        this.reactGeneratorService = reactGeneratorService;
        this.htmlProjectExportService = htmlProjectExportService;
        this.reactProjectExportService = reactProjectExportService;
        this.fixtureService = fixtureService;
    }

    @PostMapping(value = "/figma/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<SnapshotResponse> importFigma(@RequestBody ImportRequest request,
                                               @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return snapshotService.importSnapshot(resolveFileKey(request), token).map(this::toResponse);
    }

    @PostMapping("/snapshots/{snapshotId}/refresh")
    public Mono<SnapshotResponse> refresh(@PathVariable String snapshotId,
                                          @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return snapshotService.refresh(snapshotId, token).map(this::toResponse);
    }

    @GetMapping("/snapshots/{snapshotId}")
    public SnapshotResponse getSnapshot(@PathVariable String snapshotId) {
        return toResponse(snapshotService.requireSnapshot(snapshotId));
    }

    @GetMapping("/snapshots/{snapshotId}/tree")
    public Object getTree(@PathVariable String snapshotId) {
        return snapshotService.requireSnapshot(snapshotId).getDesignIr();
    }

    @GetMapping("/snapshots/{snapshotId}/structure")
    public DesignStructureSummary getStructure(@PathVariable String snapshotId) {
        return structureService.analyze(snapshotService.requireSnapshot(snapshotId).getDesignIr());
    }

    @GetMapping(value = "/snapshots/{snapshotId}/html", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getHtml(@PathVariable String snapshotId) {
        return htmlGeneratorService.generate(snapshotService.requireSnapshot(snapshotId).getDesignIr());
    }

    @GetMapping(value = "/snapshots/{snapshotId}/css", produces = "text/css")
    public String getCss(@PathVariable String snapshotId) {
        return cssGeneratorService.generate(
                snapshotService.requireSnapshot(snapshotId).getDesignIr(),
                snapshotService.getLocalAssetUrls(snapshotId));
    }

    @GetMapping(value = "/snapshots/{snapshotId}/react", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getReact(@PathVariable String snapshotId) {
        return reactGeneratorService.generate(snapshotService.requireSnapshot(snapshotId).getDesignIr());
    }

    @GetMapping(value = "/snapshots/{snapshotId}/preview", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<String> getPreview(@PathVariable String snapshotId,
                                   @RequestParam(defaultValue = "1440") double targetWidth) {
        FigmaSnapshot snapshot = snapshotService.requireSnapshot(snapshotId);
        DesignNode previewRoot = selectPreviewRoot(snapshot.getDesignIr(), targetWidth);
        return snapshotService.getPreviewImageUrls(snapshotId, previewRoot).map(previewUrls -> {
            String html = htmlGeneratorService.generateForNode(previewRoot);
            String css = cssGeneratorService.generateForNode(previewRoot, previewUrls);
            return html.replace(
                    "<link rel=\"stylesheet\" href=\"styles.css\">",
                    "<style>\n" + css + "\n</style>");
        });
    }

    @GetMapping("/snapshots/{snapshotId}/assets/{fileName}")
    public Mono<ResponseEntity<byte[]>> getAsset(@PathVariable String snapshotId,
                                                  @PathVariable String fileName) {
        return snapshotService.getAssets(snapshotId).map(assets -> {
            byte[] content = assets.zipAssetByPath().get("assets/" + fileName);
            if (content == null) throw new IllegalArgumentException("Khong tim thay snapshot asset: " + fileName);
            MediaType contentType = MediaTypeFactory.getMediaType(fileName)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox")
                    .contentType(contentType)
                    .body(content);
        });
    }

    @GetMapping("/snapshots/{snapshotId}/export")
    public Mono<ResponseEntity<byte[]>> export(@PathVariable String snapshotId,
                                                @RequestParam(defaultValue = "HTML") String format,
                                                @RequestParam(defaultValue = "AUTO") String type) {
        FigmaSnapshot snapshot = snapshotService.requireSnapshot(snapshotId);
        return snapshotService.getAssets(snapshotId).map(assets -> {
            boolean react = "REACT".equalsIgnoreCase(format);
            byte[] content = react
                    ? reactProjectExportService.export(snapshot.getDesignIr(), assets)
                    : htmlProjectExportService.export(snapshot.getDesignIr(), assets, type);
            String suffix = react ? "-react.zip" : "-html.zip";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment()
                                    .filename(snapshot.getFileKey() + suffix)
                                    .build().toString())
                    .body(content);
        });
    }

    @PostMapping(value = "/snapshots/{snapshotId}/fixture", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, String>> saveFixture(@PathVariable String snapshotId,
                                                  @RequestBody FixtureRequest request) {
        FigmaSnapshot snapshot = snapshotService.requireSnapshot(snapshotId);
        return snapshotService.getAssets(snapshotId).map(assets -> {
            Path savedAt = fixtureService.save(request.caseName(), snapshot, assets);
            return Map.of("caseName", request.caseName(), "path", savedAt.toString());
        });
    }

    private SnapshotResponse toResponse(FigmaSnapshot snapshot) {
        return SnapshotResponse.from(snapshot, structureService.analyze(snapshot.getDesignIr()));
    }

    private String resolveFileKey(ImportRequest request) {
        if (request == null) throw new IllegalArgumentException("Thieu du lieu import Figma.");
        if (request.fileKey() != null && !request.fileKey().isBlank()) return request.fileKey();
        if (request.figmaUrl() != null) {
            Matcher matcher = FIGMA_URL_PATTERN.matcher(request.figmaUrl());
            if (matcher.find()) return matcher.group(1);
        }
        throw new IllegalArgumentException("Hay nhap URL Figma hoac File Key hop le.");
    }

    private String fileName(String path) {
        return Path.of(path).getFileName().toString();
    }

    private DesignNode selectPreviewRoot(DesignNode root, double targetWidth) {
        if (root == null) return null;

        List<DesignNode> frames = new ArrayList<>();
        collectFrames(root, frames);

        List<DesignNode> screenCandidates = frames.stream()
                .filter(this::isScreenCandidate)
                .toList();
        List<DesignNode> candidates = screenCandidates.isEmpty() ? frames : screenCandidates;

        return candidates.stream()
                .max(Comparator.comparingDouble(frame -> previewScore(frame, targetWidth)))
                .orElse(root);
    }

    private void collectFrames(DesignNode node, List<DesignNode> result) {
        if (node == null) return;
        if ("FRAME".equals(node.getType())) result.add(node);
        for (DesignNode child : childrenOf(node)) collectFrames(child, result);
    }

    private boolean isScreenCandidate(DesignNode node) {
        Bounds bounds = node.getBounds();
        return bounds != null
                && bounds.width() != null
                && bounds.height() != null
                && bounds.width() >= 300
                && bounds.height() >= 500;
    }

    private double previewScore(DesignNode node, double targetWidth) {
        Bounds bounds = node.getBounds();
        double width = bounds.width();
        double height = bounds.height();
        String name = node.getName() == null ? "" : node.getName().toLowerCase();

        double score = -Math.abs(width - targetWidth) * 20;
        score += Math.min(height, 8000);
        if (height >= width * 0.6 && height <= width * 8) score += 5000;
        if (name.matches(".*(desktop|mobile|landing|homepage|home|website|web).*")) score += 8000;
        if (name.matches(".*(cover|thumbnail|component|style|guide|presentation|mockup).*")) score -= 50000;
        return score;
    }

    private List<DesignNode> childrenOf(DesignNode node) {
        return node == null || node.getChildren() == null ? List.of() : node.getChildren();
    }

    public record ImportRequest(String fileKey, String figmaUrl) {
    }

    public record FixtureRequest(String caseName) {
    }
}
