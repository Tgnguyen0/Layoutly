package com.tgnguyen.layoutlybe.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExportService {

    /**
     * Chuyen noi dung text (thuong la JSON) thanh file Word (.docx).
     * Dung font monospace de giu format JSON de doc.
     */
    public byte[] toDocx(String title, String content) throws IOException {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // Tieu de
            XWPFParagraph titlePara = doc.createParagraph();
            XWPFRun titleRun = titlePara.createRun();
            titleRun.setText(title);
            titleRun.setBold(true);
            titleRun.setFontSize(16);
            titleRun.setFontFamily("Calibri");

            // Noi dung, chia thanh nhieu doan de tranh 1 paragraph qua dai
            String[] lines = content.split("\n", -1);
            List<String> chunk = new ArrayList<>();
            int linesPerParagraph = 40;

            for (int i = 0; i < lines.length; i++) {
                chunk.add(lines[i]);
                if (chunk.size() >= linesPerParagraph || i == lines.length - 1) {
                    XWPFParagraph para = doc.createParagraph();
                    XWPFRun run = para.createRun();
                    run.setFontFamily("Consolas");
                    run.setFontSize(9);
                    for (int j = 0; j < chunk.size(); j++) {
                        run.setText(chunk.get(j));
                        run.addBreak();
                    }
                    chunk.clear();
                }
            }

            doc.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Chuyen noi dung text (thuong la JSON) thanh file PDF, tu dong xuong dong
     * va sang trang moi khi het cho.
     */
    public byte[] toPdf(String title, String content) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDFont fontBody = PDType1Font.COURIER;
            PDFont fontTitle = PDType1Font.HELVETICA_BOLD;
            float fontSize = 8f;
            float leading = 11f;
            float margin = 40f;

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream contentStream = new PDPageContentStream(document, page);

            float pageWidth = page.getMediaBox().getWidth();
            float pageHeight = page.getMediaBox().getHeight();
            float yStart = pageHeight - margin;
            float y = yStart;

            contentStream.beginText();
            contentStream.setFont(fontTitle, 14);
            contentStream.newLineAtOffset(margin, y);
            contentStream.showText(safeForPdf(title));
            contentStream.endText();
            y -= 24;

            // Ve tung dong da duoc wrap theo do rong trang
            float maxWidth = pageWidth - 2 * margin;
            List<String> wrappedLines = wrapLines(content, fontBody, fontSize, maxWidth);

            contentStream.setFont(fontBody, fontSize);
            contentStream.beginText();
            contentStream.newLineAtOffset(margin, y);

            for (String line : wrappedLines) {
                if (y <= margin) {
                    // Het trang, tao trang moi
                    contentStream.endText();
                    contentStream.close();

                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    contentStream = new PDPageContentStream(document, page);
                    contentStream.setFont(fontBody, fontSize);
                    y = yStart;
                    contentStream.beginText();
                    contentStream.newLineAtOffset(margin, y);
                }

                contentStream.showText(safeForPdf(line));
                contentStream.newLineAtOffset(0, -leading);
                y -= leading;
            }

            contentStream.endText();
            contentStream.close();

            document.save(out);
            return out.toByteArray();
        }
    }

    // PDFBox voi font Courier chuan chi ho tro WinAnsiEncoding, loai bo ky tu ngoai bang de tranh crash
    private String safeForPdf(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            sb.append(c < 32 || c > 255 ? '?' : c);
        }
        return sb.toString();
    }

    private List<String> wrapLines(String content, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> result = new ArrayList<>();
        for (String rawLine : content.split("\n", -1)) {
            if (rawLine.isEmpty()) {
                result.add("");
                continue;
            }
            StringBuilder current = new StringBuilder();
            for (char c : rawLine.toCharArray()) {
                String candidate = current.toString() + c;
                float width = font.getStringWidth(safeForPdf(candidate)) / 1000 * fontSize;
                if (width > maxWidth) {
                    result.add(current.toString());
                    current = new StringBuilder();
                    current.append(c);
                } else {
                    current.append(c);
                }
            }
            result.add(current.toString());
        }
        return result;
    }
}
