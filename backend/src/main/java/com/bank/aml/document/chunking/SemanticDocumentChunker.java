package com.bank.aml.document.chunking;

import com.bank.aml.document.parser.ExtractedDocument;
import com.bank.aml.document.parser.ExtractedPage;
import com.bank.aml.entity.Document;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Component
public class SemanticDocumentChunker implements DocumentChunker {

    private static final Pattern SECTION_PATTERN = Pattern.compile(
        "^(#{1,4}\\s+|Section\\s+\\d+|Step\\s+\\d+|\\d+\\.\\s+|[A-Z\\s]{4,60}:).*",
        Pattern.CASE_INSENSITIVE
    );

    @Override
    public List<ChunkPayload> chunk(
        ExtractedDocument extractedDocument,
        UUID documentId,
        Document docMetadata,
        int chunkSizeTokens,
        int chunkOverlapTokens
    ) {
        List<ChunkPayload> chunks = new ArrayList<>();
        int chunkIndex = 0;
        String currentSection = "General Compliance Standards";

        // Estimate ~4 characters per token
        int maxChars = chunkSizeTokens * 4;
        int overlapChars = chunkOverlapTokens * 4;

        for (ExtractedPage page : extractedDocument.pages()) {
            int pageNumber = page.pageNumber();
            String[] paragraphs = page.text().split("(\\r?\\n){2,}");

            StringBuilder currentChunkText = new StringBuilder();

            for (String rawPara : paragraphs) {
                String para = rawPara.trim();
                if (para.isEmpty()) {
                    continue;
                }

                // Check for section heading
                if (isSectionHeader(para)) {
                    currentSection = extractSectionTitle(para);
                }

                if (currentChunkText.length() + para.length() > maxChars && currentChunkText.length() > 0) {
                    // Flush current chunk
                    chunks.add(createChunk(
                        chunkIndex++,
                        pageNumber,
                        currentSection,
                        currentChunkText.toString().trim(),
                        documentId,
                        docMetadata
                    ));

                    // Carry over sliding overlap from the end of the current chunk
                    String overlap = extractOverlap(currentChunkText.toString(), overlapChars);
                    currentChunkText = new StringBuilder();
                    if (!overlap.isEmpty()) {
                        currentChunkText.append(overlap).append("\n\n");
                    }
                }

                currentChunkText.append(para).append("\n\n");
            }

            if (currentChunkText.length() > 0 && !currentChunkText.toString().trim().isEmpty()) {
                chunks.add(createChunk(
                    chunkIndex++,
                    pageNumber,
                    currentSection,
                    currentChunkText.toString().trim(),
                    documentId,
                    docMetadata
                ));
            }
        }

        return chunks;
    }

    private boolean isSectionHeader(String text) {
        String firstLine = text.split("\\r?\\n")[0].trim();
        return SECTION_PATTERN.matcher(firstLine).matches();
    }

    private String extractSectionTitle(String text) {
        String firstLine = text.split("\\r?\\n")[0].trim();
        firstLine = firstLine.replaceAll("^#+\\s*", "");
        if (firstLine.length() > 100) {
            return firstLine.substring(0, 97) + "...";
        }
        return firstLine;
    }

    private String extractOverlap(String text, int overlapChars) {
        if (text.length() <= overlapChars) {
            return text;
        }
        int start = text.length() - overlapChars;
        // Align to previous whitespace boundary to avoid cutting words
        int spaceIndex = text.indexOf(' ', start);
        if (spaceIndex != -1 && spaceIndex < text.length() - 10) {
            return text.substring(spaceIndex + 1);
        }
        return text.substring(start);
    }

    private ChunkPayload createChunk(
        int chunkIndex,
        int pageNumber,
        String section,
        String text,
        UUID documentId,
        Document docMetadata
    ) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("documentId", documentId.toString());
        metadata.put("documentTitle", docMetadata.getTitle());
        metadata.put("documentType", docMetadata.getDocumentType());
        metadata.put("version", docMetadata.getVersion());
        metadata.put("pageNumber", pageNumber);
        metadata.put("sectionTitle", section);
        metadata.put("chunkIndex", chunkIndex);

        return new ChunkPayload(chunkIndex, pageNumber, section, text, metadata);
    }
}
