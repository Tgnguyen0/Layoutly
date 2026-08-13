package com.tgnguyen.layoutlybe.controller;

import com.tgnguyen.layoutlybe.service.FigmaService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/figma")
public class FigmaController {

    private final FigmaService figmaService;
    private static final String TOKEN_HEADER = "X-Figma-Token";

    public FigmaController(FigmaService figmaService) {
        this.figmaService = figmaService;
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

    // GET /api/figma/file/{fileKey}/styles
    @GetMapping(value = "/file/{fileKey}/styles", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> getStyles(@PathVariable String fileKey,
                                   @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return figmaService.getFileStyles(fileKey, token);
    }
}
