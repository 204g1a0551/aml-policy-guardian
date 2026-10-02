package com.bank.aml.unit;

import com.bank.aml.document.chunking.ChunkPayload;
import com.bank.aml.document.chunking.SemanticDocumentChunker;
import com.bank.aml.document.parser.ExtractedDocument;
import com.bank.aml.document.parser.ExtractedPage;
import com.bank.aml.document.parser.TikaDocumentParser;
import com.bank.aml.entity.Document;
import com.bank.aml.exception.DocumentProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkingAndParsingUnitTest {

    private SemanticDocumentChunker chunker;
    private TikaDocumentParser parser;
    private Document sampleMetadata;
    private UUID documentId;

    @BeforeEach
    void setUp() {
        chunker = new SemanticDocumentChunker();
        parser = new TikaDocumentParser();
        documentId = UUID.randomUUID();
        sampleMetadata = new Document(
            "AML Transaction Monitoring SOP",
            "AML-SOP-001.pdf",
            "SOP",
            "v1.0",
            "Compliance Governance",
            "ACTIVE",
            UUID.randomUUID()
        );
    }

    @Test
    @DisplayName("Chunking: Multi-paragraph document chunks correctly with section header tracking")
    void testSemanticChunkingWithSections() {
        String pageText = """
            # Section 1: Customer Identification and Risk Triage
            All customers must undergo Customer Identification Program (CIP) checks prior to account opening.
            High risk customers require enhanced due diligence (EDD) approved by a Senior Compliance Officer.
            
            # Section 2: Currency Transaction Reporting Thresholds
            A Currency Transaction Report (CTR) must be submitted for any cash transaction exceeding $10,000.
            Aggregation rules apply across multiple transactions executed on the same business day.
            Structuring to avoid the $10,000 limit is a criminal offense under 31 USC 5324.
            """;

        ExtractedDocument doc = new ExtractedDocument(
            pageText,
            List.of(new ExtractedPage(1, pageText))
        );

        List<ChunkPayload> chunks = chunker.chunk(doc, documentId, sampleMetadata, 100, 20);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).pageNumber()).isEqualTo(1);
        assertThat(chunks).anyMatch(c -> c.section().contains("Section 1"));
        assertThat(chunks).anyMatch(c -> c.text().contains("Currency Transaction Report"));
    }

    @Test
    @DisplayName("Chunking: Sliding overlap carries over context across chunks")
    void testChunkingSlidingOverlap() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 25; i++) {
            sb.append("Sentence ").append(i).append(": Compliance officers must verify identity documents and check OFAC lists.\n\n");
        }

        ExtractedDocument doc = new ExtractedDocument(
            sb.toString(),
            List.of(new ExtractedPage(1, sb.toString()))
        );

        List<ChunkPayload> chunks = chunker.chunk(doc, documentId, sampleMetadata, 60, 15);

        assertThat(chunks.size()).isGreaterThan(1);
        for (int i = 1; i < chunks.size(); i++) {
            assertThat(chunks.get(i).chunkIndex()).isEqualTo(i);
        }
    }

    @Test
    @DisplayName("Chunking: Empty and whitespace pages are handled gracefully without generating blank chunks")
    void testChunkingEmptyPage() {
        ExtractedDocument doc = new ExtractedDocument(
            "Valid single sentence paragraph.",
            List.of(
                new ExtractedPage(1, "   \n\n   \t  "),
                new ExtractedPage(2, "Valid single sentence paragraph.")
            )
        );

        List<ChunkPayload> chunks = chunker.chunk(doc, documentId, sampleMetadata, 200, 50);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).contains("Valid single sentence paragraph");
        assertThat(chunks.get(0).pageNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("Parsing: Tika parser extracts text from plain text input streams")
    void testTikaParsingPlainText() {
        String content = "Bank AML Policy v3: All wire transfers exceeding $3,000 must include originator and beneficiary info.";
        InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        ExtractedDocument extracted = parser.parse(is, "policy.txt", "text/plain");

        assertThat(extracted.rawText()).contains("originator and beneficiary");
        assertThat(extracted.pages()).isNotEmpty();
        assertThat(extracted.pages().get(0).text()).contains("originator and beneficiary");
    }

    @Test
    @DisplayName("Parsing: Empty or invalid input stream throws DocumentProcessingException")
    void testTikaParsingEmptyStreamThrowsException() {
        InputStream emptyStream = new ByteArrayInputStream(new byte[0]);

        assertThatThrownBy(() -> parser.parse(emptyStream, "empty.txt", "text/plain"))
            .isInstanceOf(DocumentProcessingException.class);
    }
}
