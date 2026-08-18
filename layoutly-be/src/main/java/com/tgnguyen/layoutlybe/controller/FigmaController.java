package com.tgnguyen.layoutlybe.controller;

import com.tgnguyen.layoutlybe.dto.StructureSummary;
import com.tgnguyen.layoutlybe.model.UINode;
import com.tgnguyen.layoutlybe.service.AssetExportService;
import com.tgnguyen.layoutlybe.service.CssGeneratorService;
import com.tgnguyen.layoutlybe.service.FigmaParserService;
import com.tgnguyen.layoutlybe.service.FigmaService;
import com.tgnguyen.layoutlybe.service.HtmlGeneratorService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/figma")
public class FigmaController {

    private final FigmaService figmaService;
    private final FigmaParserService figmaParserService;
    private final HtmlGeneratorService htmlGeneratorService;
    private final CssGeneratorService cssGeneratorService;
    private final AssetExportService assetExportService;
    private static final String TOKEN_HEADER = "X-Figma-Token";

    public FigmaController(
            FigmaService figmaService,
            FigmaParserService figmaParserService,
            HtmlGeneratorService htmlGeneratorService,
            CssGeneratorService cssGeneratorService,
            AssetExportService assetExportService
    ) {
        this.figmaService = figmaService;
        this.figmaParserService = figmaParserService;
        this.htmlGeneratorService = htmlGeneratorService;
        this.cssGeneratorService = cssGeneratorService;
        this.assetExportService = assetExportService;
    }

    // GET /api/figma/me
    // Header X-Figma-Token la optional: neu khong gui, se dung figma.api.token cau hinh san
    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> me(@RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return figmaService.getMe(token);
    }

    // GET /api/figma/file/{fileKey}
    @GetMapping(value = "/file/{fileKey}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> getFile(@PathVariable String fileKey,
                                 @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return figmaService.getFile(fileKey, token);
    }

    // GET /api/figma/file/{fileKey}/nodes?ids=1:2,1:3
    @GetMapping(value = "/file/{fileKey}/nodes", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> getNodes(@PathVariable String fileKey,
                                  @RequestParam String ids,
                                  @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return figmaService.getFileNodes(fileKey, ids, token);
    }

    // GET /api/figma/file/{fileKey}/images?ids=1:2,1:3&format=png
    @GetMapping(value = "/file/{fileKey}/images", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> getImages(@PathVariable String fileKey,
                                   @RequestParam String ids,
                                   @RequestParam(defaultValue = "png") String format,
                                   @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return figmaService.getImages(fileKey, ids, format, token);
    }

    // GET /api/figma/file/{fileKey}/components
    @GetMapping(value = "/file/{fileKey}/components", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> getComponents(@PathVariable String fileKey,
                                       @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return figmaService.getFileComponents(fileKey, token);
    }

    // GET /api/figma/file/{fileKey}/structure
    // Endpoint moi: backend TU PARSE va phan tich cay Document->Canvas->Frame->Node,
    // tra ve so lieu tong hop (dem theo type, do sau, danh sach Auto Layout frame...)
    // Khac voi /file (chi forward JSON tho), day la bang chung backend Java thuc su
    // "hieu" cau truc, khong chi hien thi o frontend.
    @GetMapping(value = "/file/{fileKey}/structure", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<StructureSummary> getStructure(@PathVariable String fileKey,
                                                @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return figmaService.analyzeStructure(fileKey, token);
    }

    // GET /api/figma/file/{fileKey}/styles
    @GetMapping(value = "/file/{fileKey}/styles", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> getStyles(@PathVariable String fileKey,
                                   @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return figmaService.getFileStyles(fileKey, token);
    }

    // GET /api/figma/file/{fileKey}/tree — tra ve cay UI da chuan hoa, thay vi JSON tho cua Figma
    @GetMapping(value = "/file/{fileKey}/tree", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<UINode> getTree(@PathVariable String fileKey, @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return figmaService.getFile(fileKey, token)
                .map(rawJson -> {
                            try {
                                return figmaParserService.parseDocumentTree(rawJson);
                            } catch (Exception ex) {
                                throw new RuntimeException("Loi khi parse JSON thanh cay UI: " + ex.getMessage(), ex);
                            }
                        });
    }

    // GET /api/figma/file/{fileKey}/html — sinh HTML cau truc (chua co CSS) tu cay UI
    @GetMapping(value = "file/{fileKey}/html", produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<String> getHtml(@PathVariable String fileKey, @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return figmaService.getFile(fileKey, token)
                .map(rawJson -> {
                    try {
                        var tree = figmaParserService.parseDocumentTree(rawJson);
                        return htmlGeneratorService.generate(tree);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    // GET /api/figma/file/{fileKey}/export — tra ve file ZIP gom index.html + styles.css, tai xuong thuc su
    @GetMapping(value = "/file/{fileKey}/preview", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<String> getPreview(@PathVariable String fileKey, @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return figmaService.getFile(fileKey, token)
                .flatMap(rawJson -> {
                    try {
                        var tree = figmaParserService.parseDocumentTree(rawJson);
                        return assetExportService.getPreviewImageUrls(fileKey, token, tree)
                                .map(imageUrls -> {
                                    String html = htmlGeneratorService.generate(tree);
                                    String css = cssGeneratorService.generate(tree, imageUrls);
                                    return html.replace("<link rel=\"stylesheet\" href=\"styles.css\">", "<style>\n" + css + "\n</style>");
                                });
                    } catch (Exception e) {
                        return Mono.error(new RuntimeException(e));
                    }
                });
    }

    @GetMapping("/file/{fileKey}/export")
    public Mono<ResponseEntity<byte[]>> exportZip(@PathVariable String fileKey,
                                                  @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return figmaService.getFile(fileKey, token)
                .flatMap(rawJson -> {
                    try {
                        var tree = figmaParserService.parseDocumentTree(rawJson);
                        return assetExportService.exportAssets(fileKey, token, tree)
                                .map(assetBundle -> {
                                    try {
                                        String html = htmlGeneratorService.generate(tree);
                                        String css = cssGeneratorService.generate(tree, assetBundle.cssUrlByNodeId());

                                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                                            zos.putNextEntry(new ZipEntry("index.html"));
                                            zos.write(html.getBytes(StandardCharsets.UTF_8));
                                            zos.closeEntry();

                                            zos.putNextEntry(new ZipEntry("styles.css"));
                                            zos.write(css.getBytes(StandardCharsets.UTF_8));
                                            zos.closeEntry();

                                            for (var entry : assetBundle.zipAssetByPath().entrySet()) {
                                                zos.putNextEntry(new ZipEntry(entry.getKey()));
                                                zos.write(entry.getValue());
                                                zos.closeEntry();
                                            }
                                        }

                                        return ResponseEntity.ok()
                                                .contentType(MediaType.parseMediaType("application/zip"))
                                                .header(HttpHeaders.CONTENT_DISPOSITION,
                                                        ContentDisposition.attachment().filename(fileKey + "-export.zip").build().toString())
                                                .body(baos.toByteArray());
                                    } catch (Exception e) {
                                        throw new RuntimeException("Loi khi dong goi ZIP: " + e.getMessage(), e);
                                    }
                                });
                    } catch (Exception e) {
                        return Mono.error(new RuntimeException("Loi khi xuat file: " + e.getMessage(), e));
                    }
                });
    }
}
