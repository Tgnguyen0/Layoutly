package com.tgnguyen.layoutlybe.service;

import com.tgnguyen.layoutlybe.model.ir.DesignNode;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class HtmlProjectExportService {
    private final HtmlGeneratorService htmlGeneratorService;
    private final CssGeneratorService cssGeneratorService;

    public HtmlProjectExportService(HtmlGeneratorService htmlGeneratorService,
                                    CssGeneratorService cssGeneratorService) {
        this.htmlGeneratorService = htmlGeneratorService;
        this.cssGeneratorService = cssGeneratorService;
    }

    public byte[] export(DesignNode tree, AssetExportService.AssetBundle assets, String type) {
        try {
            String normalizedType = type == null || type.isBlank() ? "AUTO" : type.toUpperCase();
            Map<String, String> htmlFiles = "AUTO".equals(normalizedType)
                    ? htmlGeneratorService.generateAuto(tree)
                    : htmlGeneratorService.generateByType(tree, normalizedType);
            if (htmlFiles.isEmpty()) {
                throw new IllegalArgumentException("Khong tim thay node nao co type = " + normalizedType);
            }

            String css = cssGeneratorService.generate(tree, assets.cssUrlByNodeId());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                for (Map.Entry<String, String> entry : htmlFiles.entrySet()) {
                    put(zip, entry.getKey(), entry.getValue().getBytes(StandardCharsets.UTF_8));
                }
                put(zip, "styles.css", css.getBytes(StandardCharsets.UTF_8));
                for (Map.Entry<String, byte[]> entry : assets.zipAssetByPath().entrySet()) {
                    put(zip, entry.getKey(), entry.getValue());
                }
            }
            return output.toByteArray();
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RuntimeException("Loi khi dong goi HTML project: " + exception.getMessage(), exception);
        }
    }

    private void put(ZipOutputStream zip, String path, byte[] content) throws Exception {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content);
        zip.closeEntry();
    }
}
