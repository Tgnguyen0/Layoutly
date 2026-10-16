package com.tgnguyen.layoutlybe.controller;

import com.tgnguyen.layoutlybe.dto.DesignStructureSummary;
import com.tgnguyen.layoutlybe.dto.SnapshotResponse;
import com.tgnguyen.layoutlybe.model.ir.DesignNode;
import com.tgnguyen.layoutlybe.model.snapshot.FigmaSnapshot;
import com.tgnguyen.layoutlybe.model.snapshot.ImportSource;
import com.tgnguyen.layoutlybe.service.AssetExportService;
import com.tgnguyen.layoutlybe.service.CssGeneratorService;
import com.tgnguyen.layoutlybe.service.DesignStructureService;
import com.tgnguyen.layoutlybe.service.DesignRootSelector;
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

import java.net.URLDecoder;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api")
public class SnapshotController {
    private com.tgnguyen.layoutlybe.service.SavedProjectService savedProjects;
    @org.springframework.beans.factory.annotation.Autowired
    public void setSavedProjects(com.tgnguyen.layoutlybe.service.SavedProjectService savedProjects) { this.savedProjects = savedProjects; }
    private static final String TOKEN_HEADER = "X-Figma-Token";
    private static final Pattern FIGMA_URL_PATTERN = Pattern.compile("/(?:file|design|proto|board)/([^/?#]+)");
    private static final Pattern NODE_ID_PATTERN = Pattern.compile("[?&]node-id=([^&#]+)");

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
        ImportTarget target = resolveImportTarget(request);
        if (savedProjects == null) return snapshotService.importSnapshot(target.fileKey(), target.nodeId(), token).map(this::toResponse);
        var owner = com.tgnguyen.layoutlybe.security.CurrentUser.id();
        return snapshotService.importPrivateSnapshot(target.fileKey(), target.nodeId(), token)
                .publishOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .doOnNext(snapshot -> savedProjects.registerSnapshot(owner, snapshot)).map(this::toResponse);
    }

    @PostMapping("/snapshots/{snapshotId}/refresh")
    public Mono<SnapshotResponse> refresh(@PathVariable String snapshotId,
                                          @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        var owner = savedProjects == null ? null : com.tgnguyen.layoutlybe.security.CurrentUser.id();
        String targetId = savedProjects == null ? snapshotId : snapshotService.copySnapshot(snapshotId).getSnapshotId();
        return snapshotService.refresh(targetId, token)
                .publishOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .doOnNext(snapshot -> { if (savedProjects != null) savedProjects.registerSnapshot(owner, snapshot); }).map(this::toResponse);
    }

    @GetMapping("/snapshots/{snapshotId}")
    public SnapshotResponse getSnapshot(@PathVariable String snapshotId) {
        return toResponse(snapshotService.requireSnapshot(snapshotId));
    }

    @GetMapping("/snapshots/{snapshotId}/tree")
    public Object getTree(@PathVariable String snapshotId) {
        return selectedRoot(snapshotService.requireSnapshot(snapshotId));
    }

    @GetMapping("/snapshots/{snapshotId}/diagnostics")
    public com.tgnguyen.layoutlybe.service.RenderDiagnosticsService.Report getDiagnostics(@PathVariable String snapshotId) {
        // The existing snapshot interceptor checks the owner before this method runs.
        return new com.tgnguyen.layoutlybe.service.RenderDiagnosticsService()
                .analyze(snapshotService.requireSnapshot(snapshotId).getRawJson());
    }

    @GetMapping("/snapshots/{snapshotId}/structure")
    public DesignStructureSummary getStructure(@PathVariable String snapshotId) {
        return structureService.analyze(selectedRoot(snapshotService.requireSnapshot(snapshotId)));
    }

    @GetMapping(value = "/snapshots/{snapshotId}/html", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getHtml(@PathVariable String snapshotId) {
        return htmlGeneratorService.generate(codeRoot(snapshotService.requireSnapshot(snapshotId)));
    }

    @GetMapping(value = "/snapshots/{snapshotId}/css", produces = "text/css")
    public String getCss(@PathVariable String snapshotId) {
        FigmaSnapshot snapshot = snapshotService.requireSnapshot(snapshotId);
        return cssGeneratorService.generate(
                codeRoot(snapshot),
                snapshotService.getLocalAssetUrls(snapshotId));
    }

    @GetMapping(value = "/snapshots/{snapshotId}/react", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getReact(@PathVariable String snapshotId) {
        return reactGeneratorService.generate(codeRoot(snapshotService.requireSnapshot(snapshotId)));
    }

    public Mono<String> getPreview(String snapshotId, double targetWidth) {
        return getPreview(snapshotId, targetWidth, null);
    }

    @GetMapping(value = "/snapshots/{snapshotId}/preview", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<ResponseEntity<String>> previewResponse(@PathVariable String snapshotId,
                                   @RequestParam(defaultValue = "1440") double targetWidth,
                                   @RequestParam(required = false) String nodeId) {
        return renderPreview(snapshotId, targetWidth, nodeId).map(document -> ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .header("Server-Timing", document.serverTiming())
                .header("X-Layoutly-Root-Id", document.rootId())
                .header("X-Layoutly-Node-Count", Integer.toString(document.nodes()))
                .header("X-Layoutly-Asset-Cache", document.assetCache())
                .header("X-Layoutly-Missing-Assets", Integer.toString(document.missingAssets()))
                .contentType(MediaType.TEXT_HTML).body(document.html()));
    }

    public Mono<String> getPreview(@PathVariable String snapshotId,
                                   @RequestParam(defaultValue = "1440") double targetWidth,
                                   @RequestParam(required = false) String nodeId) {
        return renderPreview(snapshotId, targetWidth, nodeId).map(PreviewDocument::html);
    }

    private record PreviewDocument(String html, String rootId, int nodes, int missingAssets, String assetCache, String serverTiming) {}

    private Mono<PreviewDocument> renderPreview(String snapshotId, double targetWidth, String nodeId) {
        return Mono.defer(() -> {
        long started = System.nanoTime();
        FigmaSnapshot snapshot = snapshotService.requireSnapshot(snapshotId);
        DesignNode previewRoot = resolvePreviewRoot(snapshot, targetWidth, nodeId);
        String assetCache = snapshot.getAssets() == null ? "miss" : "present";
        long assetsStarted = System.nanoTime();
        double selectMs = elapsedMs(started);
        return snapshotService.getPreviewImageUrls(snapshotId, previewRoot)
                .map(previewUrls -> {
                    double assetsMs = elapsedMs(assetsStarted);
                    long htmlStarted = System.nanoTime();
                    String html = htmlGeneratorService.generateForNode(previewRoot);
                    double htmlMs = elapsedMs(htmlStarted);
                    long embedStarted = System.nanoTime();
                    // Sandboxed frames cannot send session cookies for image subrequests.
                    // Embed bytes only after the preview request passes owner authorization.
                    Map<String, String> embedded = new java.util.LinkedHashMap<>();
                    AssetExportService.AssetBundle assets = snapshot.getAssets();
                    if (assets != null) assets.cssUrlByNodeId().forEach((id, path) -> {
                        byte[] bytes = assets.zipAssetByPath().get(path);
                        if (bytes != null) {
                            String mime = MediaTypeFactory.getMediaType(path).orElse(MediaType.APPLICATION_OCTET_STREAM).toString();
                            embedded.put(id, "data:" + mime + ";base64," + java.util.Base64.getEncoder().encodeToString(bytes));
                        }
                    });
                    double embedMs = elapsedMs(embedStarted);
                    long cssStarted = System.nanoTime();
                    String css = cssGeneratorService.generateForNode(previewRoot, embedded);
                    double cssMs = elapsedMs(cssStarted);
                    String body = html.replace(
                            "<link rel=\"stylesheet\" href=\"styles.css\">",
                            "<style>\n" + css + "\n</style>");
                    String timing = String.format(java.util.Locale.ROOT,
                            "select;dur=%.3f, assets;dur=%.3f, embed;dur=%.3f, html;dur=%.3f, css;dur=%.3f, preview;dur=%.3f",
                            selectMs, assetsMs, embedMs, htmlMs, cssMs, elapsedMs(started));
                    return new PreviewDocument(body, previewRoot.getId(), countNodes(previewRoot),
                            snapshotService.missingPreviewAssets(snapshotId, previewRoot), assetCache, timing);
                });
        });
    }

    private double elapsedMs(long started) { return (System.nanoTime() - started) / 1_000_000.0; }
    private int countNodes(DesignNode node) {
        return node == null ? 0 : 1 + (node.getChildren() == null ? 0 : node.getChildren().stream().mapToInt(this::countNodes).sum());
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
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
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
        var owner = savedProjects == null ? null : com.tgnguyen.layoutlybe.security.CurrentUser.id();
        FigmaSnapshot snapshot = snapshotService.requireSnapshot(snapshotId);
        DesignNode exportRoot = DesignRootSelector.defaultPage(selectedRoot(snapshot));
        return snapshotService.getAssets(snapshotId).publishOn(reactor.core.scheduler.Schedulers.boundedElastic()).map(assets -> {
            boolean react = "REACT".equalsIgnoreCase(format);
            byte[] content = react
                    ? reactProjectExportService.export(exportRoot, assets)
                    : htmlProjectExportService.export(exportRoot, assets, type);
            String suffix = react ? "-react.zip" : "-html.zip";
            if (savedProjects != null) savedProjects.recordExport(owner, snapshotId, format, null, content.length);
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
        return SnapshotResponse.from(snapshot, structureService.analyze(selectedRoot(snapshot)));
    }

    private ImportTarget resolveImportTarget(ImportRequest request) {
        if (request == null) throw new IllegalArgumentException("Thieu du lieu import Figma.");
        String fileKey = null;
        String nodeId = hasText(request.nodeId()) ? normalizeNodeId(request.nodeId()) : null;
        if (hasText(request.fileKey())) fileKey = request.fileKey();
        if (request.figmaUrl() != null) {
            Matcher matcher = FIGMA_URL_PATTERN.matcher(request.figmaUrl());
            if (!hasText(fileKey) && matcher.find()) fileKey = matcher.group(1);

            Matcher nodeMatcher = NODE_ID_PATTERN.matcher(request.figmaUrl());
            if (!hasText(nodeId) && nodeMatcher.find()) {
                nodeId = normalizeNodeId(URLDecoder.decode(nodeMatcher.group(1), StandardCharsets.UTF_8));
            }
        }
        if (hasText(fileKey)) return new ImportTarget(fileKey, nodeId);
        throw new IllegalArgumentException("Hay nhap URL Figma hoac File Key hop le.");
    }

    private DesignNode selectedRoot(FigmaSnapshot snapshot) {
        if (snapshot == null || snapshot.getDesignIr() == null) return null;
        if (snapshot.getSource() != ImportSource.FIGMA_REST) return snapshot.getDesignIr();
        DesignNode selected = findNodeById(snapshot.getDesignIr(), snapshot.getRootNodeId());
        return selected == null ? snapshot.getDesignIr() : selected;
    }

    private DesignNode codeRoot(FigmaSnapshot snapshot) {
        DesignNode root = selectedRoot(snapshot);
        return isContainer(root) ? selectPreviewRoot(root, 1440) : root;
    }

    private DesignNode resolvePreviewRoot(FigmaSnapshot snapshot, double targetWidth, String nodeId) {
        DesignNode root = selectedRoot(snapshot);
        DesignNode requested = hasText(nodeId) ? findNodeById(root, nodeId) : null;
        DesignNode source = requested == null ? root : requested;
        return isContainer(source) ? selectPreviewRoot(source, targetWidth) : source;
    }

    private boolean isContainer(DesignNode node) {
        return node != null && ("DOCUMENT".equals(node.getType()) || "CANVAS".equals(node.getType()));
    }

    private DesignNode findNodeById(DesignNode node, String nodeId) {
        if (node == null || !hasText(nodeId)) return null;
        if (nodeId.equals(node.getId())) return node;
        for (DesignNode child : childrenOf(node)) {
            DesignNode found = findNodeById(child, nodeId);
            if (found != null) return found;
        }
        return null;
    }

    private String normalizeNodeId(String nodeId) {
        return nodeId == null ? null : nodeId.trim().replace('-', ':');
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String fileName(String path) {
        return Path.of(path).getFileName().toString();
    }

    private DesignNode selectPreviewRoot(DesignNode root, double targetWidth) {
        return DesignRootSelector.preview(root, targetWidth);
    }

    private List<DesignNode> childrenOf(DesignNode node) {
        return node == null || node.getChildren() == null ? List.of() : node.getChildren();
    }

    public record ImportRequest(String fileKey, String figmaUrl, String nodeId) {
        public ImportRequest(String fileKey, String figmaUrl) {
            this(fileKey, figmaUrl, null);
        }
    }

    private record ImportTarget(String fileKey, String nodeId) {
    }

    public record FixtureRequest(String caseName) {
    }
}
