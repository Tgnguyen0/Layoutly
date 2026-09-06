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
import java.util.Map;
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

    // GET /api/figma/file/{fileKey}/css — sinh CSS rieng, tach biet hoan toan voi HTML.
    // Dung chung voi /html o tren de co du 2 file rieng le (khong dong goi ZIP, khong inline).
    // Luu y: anh (background-image) se KHONG co URL o day, vi lay URL preview tu Figma
    // can goi them 1 request rieng (xem AssetExportService.getPreviewImageUrls) - endpoint
    // nay chi phuc vu xem nhanh phan style layout/mau/chu, chua co asset that.
    @GetMapping(value = "/file/{fileKey}/css", produces = "text/css")
    public Mono<String> getCss(@PathVariable String fileKey, @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return figmaService.getFile(fileKey, token)
                .map(rawJson -> {
                    try {
                        var tree = figmaParserService.parseDocumentTree(rawJson);
                        return cssGeneratorService.generate(tree);
                    } catch (Exception e) {
                        throw new RuntimeException("Loi khi sinh CSS: " + e.getMessage(), e);
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

    // GET /api/figma/file/{fileKey}/export?type=FRAME (type la optional, mac dinh FRAME)
    // Xuat ZIP gom NHIEU file .html rieng biet - moi node co type khop se thanh 1 file,
    // KHONG con gop chung tat ca vao 1 index.html duy nhat nhu truoc nua.
    // Vi du: mac dinh type=FRAME -> file dat theo ten tung Frame (hero-section.html,
    // footer.html...). Muon tach theo don vi khac (SECTION, COMPONENT...) thi truyen
    // ?type=SECTION. Neu khong tim thay node nao khop type, tra loi HTTP 400 kem message
    // ro rang thay vi zip rong kho hieu.
    @GetMapping("/file/{fileKey}/export")
    public Mono<ResponseEntity<byte[]>> exportZip(@PathVariable String fileKey,
                                                  @RequestParam(defaultValue = "CANVAS") String type,
                                                  @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return figmaService.getFile(fileKey, token)
                .flatMap(rawJson -> {
                    try {
                        var tree = figmaParserService.parseDocumentTree(rawJson);
                        return assetExportService.exportAssets(fileKey, token, tree)
                                .map(assetBundle -> {
                                    try {
                                        Map<String, String> htmlFiles = htmlGeneratorService.generateByType(tree, type);
                                        if (htmlFiles.isEmpty()) {
                                            throw new IllegalArgumentException(
                                                    "Khong tim thay node nao co type = " + type
                                                            + " trong file nay. Goi /file/" + fileKey
                                                            + "/structure de xem cac type dang co.");
                                        }
                                        String css = cssGeneratorService.generate(tree, assetBundle.cssUrlByNodeId());

                                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                                            for (var entry : htmlFiles.entrySet()) {
                                                zos.putNextEntry(new ZipEntry(entry.getKey()));
                                                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                                                zos.closeEntry();
                                            }

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
                                    } catch (IllegalArgumentException e) {
                                        throw e; // de GlobalExceptionHandler tra ve 400 thay vi 500
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
