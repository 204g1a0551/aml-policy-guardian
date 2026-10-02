package com.bank.aml;

import com.bank.aml.config.RagProperties;
import com.bank.aml.document.DocumentIngestionPipeline;
import com.bank.aml.document.DocumentStorageService;
import com.bank.aml.embedding.EmbeddingGenerationService;
import com.bank.aml.entity.Document;
import com.bank.aml.entity.DocumentChunk;
import com.bank.aml.entity.User;
import com.bank.aml.exception.DocumentProcessingException;
import com.bank.aml.rag.VectorRetrievalService;
import com.bank.aml.repository.ChunkSimilarityProjection;
import com.bank.aml.repository.DocumentChunkRepository;
import com.bank.aml.repository.DocumentRepository;
import com.bank.aml.repository.UserRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class RagIngestionPipelineIntegrationTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private DocumentIngestionPipeline documentIngestionPipeline;

    @Autowired
    private DocumentStorageService documentStorageService;

    @Autowired
    private EmbeddingGenerationService embeddingGenerationService;

    @Autowired
    private VectorRetrievalService vectorRetrievalService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RagProperties ragProperties;

    private User adminUser;

    @BeforeEach
    void setUp() {
        adminUser = userRepository.findByUsername("admin").orElseThrow();
    }

    @Test
    @DisplayName("1. End-to-end PDF ingestion: text extraction, semantic chunking, vector embedding, and status READY")
    void testIngestValidPdfDocument() throws IOException {
        String testId = UUID.randomUUID().toString().substring(0, 8);
        byte[] pdfBytes = generateMultiPagePdf(
            "Section 1: Automated Transaction Monitoring Policy " + testId + "\n" +
            "The bank monitors all customer accounts for suspicious velocity and smurfing indicators.",
            "Section 2: High-Risk Jurisdictions and Sanctions Screening " + testId + "\n" +
            "All outbound wire transfers to sanctioned jurisdictions must be flagged within 4 hours."
        );

        String filename = "AML-POL-TEST-" + testId + ".pdf";
        String storagePath = documentStorageService.store(new ByteArrayInputStream(pdfBytes), filename, "application/pdf");

        Document doc = new Document(
            "Automated Surveillance Policy " + testId,
            filename,
            "POLICY",
            "v1.0",
            "Compliance Dept",
            "UPLOADED",
            adminUser.getId()
        );
        doc.setStoragePath(storagePath);
        doc.setMimeType("application/pdf");
        doc.setFileSizeBytes((long) pdfBytes.length);
        doc.setSha256Checksum(UUID.randomUUID().toString());
        Document savedDoc = documentRepository.save(doc);

        // Execute Ingestion Pipeline
        documentIngestionPipeline.ingest(savedDoc.getId());

        // Verify status transitioned to READY
        Document updated = documentRepository.findById(savedDoc.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("READY");
        assertThat(updated.getErrorMessage()).isNull();

        // Verify Chunks in PostgreSQL pgvector
        List<DocumentChunk> chunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(savedDoc.getId());
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.size()).isGreaterThanOrEqualTo(1);

        DocumentChunk firstChunk = chunks.get(0);
        assertThat(firstChunk.getChunkText()).contains("Section 1: Automated Transaction Monitoring Policy");
        assertThat(firstChunk.getEmbedding()).isNotBlank();
        assertThat(firstChunk.getPageNumber()).isGreaterThanOrEqualTo(1);
        assertThat(firstChunk.getSection()).isNotBlank();
    }

    @Test
    @DisplayName("2. End-to-end DOCX ingestion: section extraction, chunking, and pgvector persistence")
    void testIngestValidDocxDocument() throws IOException {
        String testId = UUID.randomUUID().toString().substring(0, 8);
        byte[] docxBytes = generateDocx(
            "Section 1: Wire Transfer Verification Rules " + testId,
            "Every cross-border payment exceeding $25,000 requires two-party senior compliance verification.",
            "Section 2: Shell Corporation Identification " + testId,
            "Accounts without physical operational facilities or domestic payroll are treated as shell entities."
        );

        String filename = "AML-SOP-TEST-" + testId + ".docx";
        String storagePath = documentStorageService.store(new ByteArrayInputStream(docxBytes), filename, "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        Document doc = new Document(
            "Wire Transfer Rules " + testId,
            filename,
            "PROCEDURE",
            "v2.1",
            "FIU Unit",
            "UPLOADED",
            adminUser.getId()
        );
        doc.setStoragePath(storagePath);
        doc.setMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        doc.setFileSizeBytes((long) docxBytes.length);
        doc.setSha256Checksum(UUID.randomUUID().toString());
        Document savedDoc = documentRepository.save(doc);

        documentIngestionPipeline.ingest(savedDoc.getId());

        Document updated = documentRepository.findById(savedDoc.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("READY");

        List<DocumentChunk> chunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(savedDoc.getId());
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).getChunkText()).contains("Wire Transfer Verification Rules");
        assertThat(chunks.get(0).getEmbedding()).isNotBlank();
    }

    @Test
    @DisplayName("3. End-to-end TXT ingestion using synthetic AML compliance procedure")
    void testIngestSyntheticAmlProcedureTxt() throws IOException {
        Path txtPath = Path.of("../documents/AML-SOP-001_Transaction_Monitoring_Procedure.txt");
        assertThat(Files.exists(txtPath)).isTrue();
        byte[] txtBytes = Files.readAllBytes(txtPath);

        String testId = UUID.randomUUID().toString().substring(0, 8);
        String filename = "AML-SOP-001_TM_Procedure_" + testId + ".txt";
        String storagePath = documentStorageService.store(new ByteArrayInputStream(txtBytes), filename, "text/plain");

        Document doc = new Document(
            "Transaction Monitoring SOP " + testId,
            filename,
            "PROCEDURE",
            "v2.4",
            "FIU",
            "UPLOADED",
            adminUser.getId()
        );
        doc.setStoragePath(storagePath);
        doc.setMimeType("text/plain");
        doc.setFileSizeBytes((long) txtBytes.length);
        doc.setSha256Checksum(UUID.randomUUID().toString());
        Document savedDoc = documentRepository.save(doc);

        documentIngestionPipeline.ingest(savedDoc.getId());

        Document updated = documentRepository.findById(savedDoc.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("READY");

        List<DocumentChunk> chunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(savedDoc.getId());
        assertThat(chunks.size()).isGreaterThanOrEqualTo(2);

        // Verify semantic chunks preserved headers and content
        boolean hasScenarioRule = chunks.stream().anyMatch(c -> c.getChunkText().contains("Scenario TM-RULE-101"));
        assertThat(hasScenarioRule).isTrue();
    }

    @Test
    @DisplayName("4. Vector retrieval verifies ingested chunks can be queried with cosine similarity")
    void testVectorRetrievalAgainstIngestedDocument() throws IOException {
        String uniqueTopic = "Hawala Banking Brokerage Ring " + UUID.randomUUID();
        String content = "Section 1: Hawala Detection Protocols\n" +
            "Brokers using informal value transfer systems (IVTS) and " + uniqueTopic + " " +
            "frequently balance ledgers using third-party commercial trade invoices in high-risk free zones.";

        byte[] txtBytes = content.getBytes(StandardCharsets.UTF_8);
        String filename = "Hawala_Test_" + UUID.randomUUID() + ".txt";
        String storagePath = documentStorageService.store(new ByteArrayInputStream(txtBytes), filename, "text/plain");

        Document doc = new Document(
            "Hawala Protocols",
            filename,
            "GUIDELINE",
            "v1.0",
            "Special Investigations",
            "UPLOADED",
            adminUser.getId()
        );
        doc.setStoragePath(storagePath);
        doc.setMimeType("text/plain");
        doc.setFileSizeBytes((long) txtBytes.length);
        doc.setSha256Checksum(UUID.randomUUID().toString());
        Document savedDoc = documentRepository.save(doc);

        documentIngestionPipeline.ingest(savedDoc.getId());

        // Perform vector search using the exact phrase
        float[] queryVector = embeddingGenerationService.generateEmbedding("Section 1: Hawala Detection Protocols " + uniqueTopic);
        List<ChunkSimilarityProjection> searchResults = vectorRetrievalService.searchSimilarChunks(
            queryVector,
            0.50,
            ragProperties.getTopK()
        );

        assertThat(searchResults).isNotEmpty();
        ChunkSimilarityProjection topResult = searchResults.get(0);
        assertThat(topResult.getChunkText()).contains(uniqueTopic);
        assertThat(topResult.getSimilarity()).isGreaterThanOrEqualTo(0.50);
    }

    @Test
    @DisplayName("5. Failure recovery: Corrupted document transitions to FAILED and cleans up any partial chunks")
    void testFailureRecoveryOnCorruptDocument() {
        // Create an unreadable / non-existent storage path
        String bogusStoragePath = "compliance_storage/corrupt_file_" + UUID.randomUUID() + ".pdf";

        Document doc = new Document(
            "Corrupt Ingestion Test",
            "corrupt.pdf",
            "POLICY",
            "v1.0",
            "Testing",
            "UPLOADED",
            adminUser.getId()
        );
        doc.setStoragePath(bogusStoragePath);
        doc.setMimeType("application/pdf");
        doc.setFileSizeBytes(100L);
        doc.setSha256Checksum(UUID.randomUUID().toString());
        Document savedDoc = documentRepository.save(doc);

        // Pre-insert a dummy chunk to verify failure cleanup deletes partial chunks
        DocumentChunk partialChunk = new DocumentChunk(
            savedDoc.getId(),
            "Orphan chunk before failure",
            0,
            1,
            "Draft",
            "{}",
            VectorRetrievalService.formatVector(new float[1536])
        );
        documentChunkRepository.save(partialChunk);
        assertThat(documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(savedDoc.getId())).hasSize(1);

        // Ingestion should fail
        assertThatThrownBy(() -> documentIngestionPipeline.ingest(savedDoc.getId()))
            .isInstanceOf(DocumentProcessingException.class);

        // Verify status is FAILED and useful error message is recorded
        Document refreshed = documentRepository.findById(savedDoc.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo("FAILED");
        assertThat(refreshed.getErrorMessage()).isNotBlank();

        // Verify partial chunks were cleaned up
        List<DocumentChunk> remainingChunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(savedDoc.getId());
        assertThat(remainingChunks).isEmpty();
    }

    @Test
    @DisplayName("6. Re-ingestion idempotency: Reprocessing a document replaces previous chunks cleanly")
    void testReingestionIdempotency() throws IOException {
        String testId = UUID.randomUUID().toString().substring(0, 8);
        byte[] pdfBytes = generateMultiPagePdf(
            "Section 1: Initial KYC Verification " + testId + "\nVerify primary passport."
        );

        String filename = "Idempotency_Test_" + testId + ".pdf";
        String storagePath = documentStorageService.store(new ByteArrayInputStream(pdfBytes), filename, "application/pdf");

        Document doc = new Document(
            "Idempotency Test " + testId,
            filename,
            "PROCEDURE",
            "v1.0",
            "KYC",
            "UPLOADED",
            adminUser.getId()
        );
        doc.setStoragePath(storagePath);
        doc.setMimeType("application/pdf");
        doc.setFileSizeBytes((long) pdfBytes.length);
        doc.setSha256Checksum(UUID.randomUUID().toString());
        Document savedDoc = documentRepository.save(doc);

        // First ingestion
        documentIngestionPipeline.ingest(savedDoc.getId());
        List<DocumentChunk> firstPassChunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(savedDoc.getId());
        int firstCount = firstPassChunks.size();
        assertThat(firstCount).isGreaterThan(0);

        // Re-ingest
        documentIngestionPipeline.ingest(savedDoc.getId());
        List<DocumentChunk> secondPassChunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(savedDoc.getId());

        // Count should not duplicate or grow unbounded
        assertThat(secondPassChunks.size()).isEqualTo(firstCount);
        Document updated = documentRepository.findById(savedDoc.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("READY");
    }

    // Helper: generate PDF with variable pages
    private byte[] generateMultiPagePdf(String... pages) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (String pageText : pages) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                    stream.beginText();
                    stream.setFont(PDType1Font.HELVETICA, 12);
                    stream.newLineAtOffset(50, 700);
                    for (String line : pageText.split("\n")) {
                        stream.showText(line);
                        stream.newLineAtOffset(0, -15);
                    }
                    stream.endText();
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private byte[] generateDocx(String header1, String p1, String header2, String p2) throws IOException {
        try (XWPFDocument docx = new XWPFDocument()) {
            XWPFParagraph h1 = docx.createParagraph();
            h1.createRun().setText(header1);

            XWPFParagraph body1 = docx.createParagraph();
            body1.createRun().setText(p1);

            XWPFParagraph h2 = docx.createParagraph();
            h2.createRun().setText(header2);

            XWPFParagraph body2 = docx.createParagraph();
            body2.createRun().setText(p2);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            docx.write(baos);
            return baos.toByteArray();
        }
    }
}
