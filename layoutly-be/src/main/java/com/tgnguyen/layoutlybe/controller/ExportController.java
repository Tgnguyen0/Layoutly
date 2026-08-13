package com.tgnguyen.layoutlybe.controller;

import com.tgnguyen.layoutlybe.service.ExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @PostMapping("/docx")
    public ResponseEntity<byte[]> exportDocx(@RequestBody ExportRequest request) throws IOException {
        String filename = safeFilename(request.getFilename()) + ".docx";
        byte[] bytes = exportService.toDocx(request.getFilename(), request.getContent());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(bytes);
    }

    @PostMapping("/pdf")
    public ResponseEntity<byte[]> exportPdf(@RequestBody ExportRequest request) throws IOException {
        String filename = safeFilename(request.getFilename()) + ".pdf";
        byte[] bytes = exportService.toPdf(request.getFilename(), request.getContent());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(bytes);
    }

    private String safeFilename(String raw) {
        if (raw == null || raw.isBlank()) return "figma-export";
        return raw.replaceAll("[^a-zA-Z0-9-_]", "_");
    }
}
