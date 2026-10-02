package com.bank.aml.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SyntheticDocumentGenerator {

    public static byte[] generatePdfBytes(Path sourceTxt, String salt) throws IOException {
        List<String> rawLines = new ArrayList<>(Files.readAllLines(sourceTxt));
        if (salt != null && !salt.isBlank()) {
            rawLines.add("");
            rawLines.add("Document Version Salt: " + salt);
        }
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDPageContentStream stream = new PDPageContentStream(doc, page);
            stream.beginText();
            stream.setFont(PDType1Font.HELVETICA, 10);
            stream.newLineAtOffset(50, 750);

            float y = 750;
            float leading = 14;

            for (String rawLine : rawLines) {
                List<String> wrapped = wrapLine(rawLine, 80);
                for (String line : wrapped) {
                    if (y < 60) {
                        stream.endText();
                        stream.close();
                        page = new PDPage(PDRectangle.LETTER);
                        doc.addPage(page);
                        stream = new PDPageContentStream(doc, page);
                        stream.beginText();
                        stream.setFont(PDType1Font.HELVETICA, 10);
                        stream.newLineAtOffset(50, 750);
                        y = 750;
                    }
                    String clean = line.replace("\t", "    ").replaceAll("[^\\x20-\\x7E]", "");
                    stream.showText(clean);
                    stream.newLineAtOffset(0, -leading);
                    y -= leading;
                }
            }

            stream.endText();
            stream.close();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    public static byte[] generateDocxBytes(Path sourceTxt, String salt) throws IOException {
        List<String> lines = new ArrayList<>(Files.readAllLines(sourceTxt));
        if (salt != null && !salt.isBlank()) {
            lines.add("");
            lines.add("Document Version Salt: " + salt);
        }
        try (XWPFDocument docx = new XWPFDocument()) {
            for (String line : lines) {
                XWPFParagraph p = docx.createParagraph();
                String trimmed = line.trim();
                if (trimmed.startsWith("Section ") || trimmed.startsWith("Step ") || trimmed.startsWith("AML-")) {
                    p.setAlignment(ParagraphAlignment.LEFT);
                    XWPFRun r = p.createRun();
                    r.setBold(true);
                    r.setFontSize(12);
                    r.setText(trimmed);
                } else if (!trimmed.isEmpty()) {
                    XWPFRun r = p.createRun();
                    r.setFontSize(10);
                    r.setText(trimmed);
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            docx.write(baos);
            return baos.toByteArray();
        }
    }

    public static void generatePdfFromText(Path sourceTxt, Path targetPdf) throws IOException {
        byte[] bytes = generatePdfBytes(sourceTxt, null);
        Files.write(targetPdf, bytes);
    }

    public static void generateDocxFromText(Path sourceTxt, Path targetDocx) throws IOException {
        byte[] bytes = generateDocxBytes(sourceTxt, null);
        Files.write(targetDocx, bytes);
    }

    private static List<String> wrapLine(String text, int maxChars) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            result.add("");
            return result;
        }

        String remaining = text;
        while (remaining.length() > maxChars) {
            int spaceIdx = remaining.lastIndexOf(' ', maxChars);
            if (spaceIdx == -1) {
                spaceIdx = maxChars;
            }
            result.add(remaining.substring(0, spaceIdx));
            remaining = remaining.substring(spaceIdx).trim();
        }
        if (!remaining.isEmpty()) {
            result.add(remaining);
        }
        return result;
    }
}
