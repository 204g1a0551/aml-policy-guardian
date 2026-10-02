package com.bank.aml;

import com.bank.aml.config.RagProperties;
import com.bank.aml.dto.request.DocumentUploadRequest;
import com.bank.aml.dto.response.DocumentResponse;
import com.bank.aml.embedding.EmbeddingGenerationService;
import com.bank.aml.entity.DocumentChunk;
import com.bank.aml.entity.User;
import com.bank.aml.rag.VectorRetrievalService;
import com.bank.aml.repository.ChunkSimilarityProjection;
import com.bank.aml.repository.DocumentChunkRepository;
import com.bank.aml.repository.DocumentRepository;
import com.bank.aml.repository.UserRepository;
import com.bank.aml.service.DocumentService;
import com.bank.aml.util.SyntheticDocumentGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AllSyntheticDocumentsIngestionIntegrationTest {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private EmbeddingGenerationService embeddingGenerationService;

    @Autowired
    private VectorRetrievalService vectorRetrievalService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RagProperties ragProperties;

    private User adminUser;
    private Path documentsDir;

    @BeforeEach
    void setUp() {
        adminUser = userRepository.findByUsername("admin").orElseThrow();
        documentsDir = Paths.get("../documents").toAbsolutePath().normalize();
    }

    @Test
    @DisplayName("End-to-End: Ingest all 5 synthetic AML documents across PDF, DOCX, and TXT, and query with pgvector")
    void testIngestAllFiveSyntheticAmlDocumentsAndRetrieve() throws IOException {
        // 1. Generate PDF and DOCX versions of synthetic documents in documents/ directory
        Path sop1Txt = documentsDir.resolve("AML-SOP-001_Transaction_Monitoring_Procedure.txt");
        Path pol2Txt = documentsDir.resolve("AML-POL-002_Customer_Risk_Assessment.txt");
        Path sop3Txt = documentsDir.resolve("AML-SOP-003_Suspicious_Activity_Investigation.txt");
        Path guide4Txt = documentsDir.resolve("AML-GUIDE-004_Transaction_Escalation.txt");
        Path sop5Txt = documentsDir.resolve("AML-SOP-005_KYC_Verification_Procedure.txt");

        assertThat(Files.exists(sop1Txt)).isTrue();
        assertThat(Files.exists(pol2Txt)).isTrue();
        assertThat(Files.exists(sop3Txt)).isTrue();
        assertThat(Files.exists(guide4Txt)).isTrue();
        assertThat(Files.exists(sop5Txt)).isTrue();

        Path sop1Pdf = documentsDir.resolve("AML-SOP-001_Transaction_Monitoring_Procedure.pdf");
        Path pol2Docx = documentsDir.resolve("AML-POL-002_Customer_Risk_Assessment.docx");
        Path sop3Pdf = documentsDir.resolve("AML-SOP-003_Suspicious_Activity_Investigation.pdf");
        Path guide4Docx = documentsDir.resolve("AML-GUIDE-004_Transaction_Escalation.docx");

        SyntheticDocumentGenerator.generatePdfFromText(sop1Txt, sop1Pdf);
        SyntheticDocumentGenerator.generateDocxFromText(pol2Txt, pol2Docx);
        SyntheticDocumentGenerator.generatePdfFromText(sop3Txt, sop3Pdf);
        SyntheticDocumentGenerator.generateDocxFromText(guide4Txt, guide4Docx);

        assertThat(Files.exists(sop1Pdf)).isTrue();
        assertThat(Files.exists(pol2Docx)).isTrue();
        assertThat(Files.exists(sop3Pdf)).isTrue();
        assertThat(Files.exists(guide4Docx)).isTrue();

        // Unique run salt to ensure idempotency across multiple test runs
        String salt = UUID.randomUUID().toString().substring(0, 8);

        // 2. Ingest Document 1: PDF - AML Transaction Monitoring Procedure
        byte[] doc1Bytes = SyntheticDocumentGenerator.generatePdfBytes(sop1Txt, salt);
        MockMultipartFile file1 = new MockMultipartFile(
            "file", "AML-SOP-001_TM_Procedure_" + salt + ".pdf", "application/pdf", doc1Bytes
        );
        DocumentResponse resp1 = documentService.uploadDocument(file1, new DocumentUploadRequest(
            "AML Transaction Monitoring Procedure " + salt, "PROCEDURE", "v2.4", "Financial Intelligence Unit"
        ), adminUser.getId());
        assertThat(resp1.status()).isEqualTo("READY");

        // 3. Ingest Document 2: DOCX - Customer Risk Assessment Policy
        byte[] doc2Bytes = SyntheticDocumentGenerator.generateDocxBytes(pol2Txt, salt);
        MockMultipartFile file2 = new MockMultipartFile(
            "file", "AML-POL-002_Customer_Risk_" + salt + ".docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", doc2Bytes
        );
        DocumentResponse resp2 = documentService.uploadDocument(file2, new DocumentUploadRequest(
            "Customer Risk Assessment Policy " + salt, "POLICY", "v3.1", "Global AML Compliance"
        ), adminUser.getId());
        assertThat(resp2.status()).isEqualTo("READY");

        // 4. Ingest Document 3: PDF - Suspicious Activity Investigation Procedure
        byte[] doc3Bytes = SyntheticDocumentGenerator.generatePdfBytes(sop3Txt, salt);
        MockMultipartFile file3 = new MockMultipartFile(
            "file", "AML-SOP-003_SAR_Investigation_" + salt + ".pdf", "application/pdf", doc3Bytes
        );
        DocumentResponse resp3 = documentService.uploadDocument(file3, new DocumentUploadRequest(
            "Suspicious Activity Investigation Procedure " + salt, "PROCEDURE", "v4.0", "FIU Investigations"
        ), adminUser.getId());
        assertThat(resp3.status()).isEqualTo("READY");

        // 5. Ingest Document 4: DOCX - Transaction Escalation Guidelines
        byte[] doc4Bytes = SyntheticDocumentGenerator.generateDocxBytes(guide4Txt, salt);
        MockMultipartFile file4 = new MockMultipartFile(
            "file", "AML-GUIDE-004_Escalation_" + salt + ".docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", doc4Bytes
        );
        DocumentResponse resp4 = documentService.uploadDocument(file4, new DocumentUploadRequest(
            "Transaction Escalation Guidelines " + salt, "GUIDELINE", "v2.8", "Office of the MLRO"
        ), adminUser.getId());
        assertThat(resp4.status()).isEqualTo("READY");

        // 6. Ingest Document 5: TXT - KYC Verification Procedure
        byte[] doc5Bytes = (Files.readString(sop5Txt) + "\n\nTest Run Salt: " + salt).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MockMultipartFile file5 = new MockMultipartFile(
            "file", "AML-SOP-005_KYC_Verification_" + salt + ".txt", "text/plain", doc5Bytes
        );
        DocumentResponse resp5 = documentService.uploadDocument(file5, new DocumentUploadRequest(
            "KYC Verification Procedure " + salt, "PROCEDURE", "v3.5", "Central Onboarding Operations"
        ), adminUser.getId());
        assertThat(resp5.status()).isEqualTo("READY");

        // 7. Verify all documents have chunks with embeddings in PostgreSQL
        List<UUID> docIds = List.of(resp1.id(), resp2.id(), resp3.id(), resp4.id(), resp5.id());
        for (UUID docId : docIds) {
            List<DocumentChunk> chunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(docId);
            assertThat(chunks).isNotEmpty();
            for (DocumentChunk chunk : chunks) {
                assertThat(chunk.getEmbedding()).isNotBlank();
                assertThat(chunk.getEmbedding()).startsWith("[");
                assertThat(chunk.getEmbedding()).endsWith("]");
                assertThat(chunk.getChunkText()).isNotBlank();
            }
        }

        // 8. Test Vector Retrieval Question 1: TM-RULE-101 Structuring rules
        assertQuestionRetrieval(
            "Scenario TM-RULE-101 Structuring and Smurfing cash deposits between $7,000 and $9,999",
            "TM-RULE-101"
        );

        // 9. Test Vector Retrieval Question 2: Beneficial Ownership UBO 25% threshold
        assertQuestionRetrieval(
            "Beneficial Ownership Declaration 25% or more equity interests of legal entity UBO",
            "25%"
        );

        // 10. Test Vector Retrieval Question 3: Suspicious Activity Report SAR Narrative 5 Ws
        assertQuestionRetrieval(
            "SAR Narrative Standards The 5 Ws WHO WHAT WHERE WHEN WHY HOW",
            "5 Ws"
        );

        // 11. Test Vector Retrieval Question 4: Priority 1 Escalation 4-Hour Response SLA for OFAC Sanctions
        assertQuestionRetrieval(
            "Critical Priority 1 4-Hour Response SLA OFAC Sanctions terrorism financing",
            "4-Hour"
        );

        // 12. Test Vector Retrieval Question 5: High Risk Customer Periodic Review 12 months
        assertQuestionRetrieval(
            "High Risk Customers Reviewed every 12 months annual full review UBO",
            "12 months"
        );
    }

    private void assertQuestionRetrieval(String queryPhrase, String expectedSnippet) {
        float[] queryEmbedding = embeddingGenerationService.generateEmbedding(queryPhrase);
        // Using 0.10 threshold to account for dense bag-of-words token dispersion between short queries and large chunks in offline fallback mode
        List<ChunkSimilarityProjection> results = vectorRetrievalService.searchSimilarChunks(
            queryEmbedding,
            0.10,
            ragProperties.getTopK()
        );

        assertThat(results).isNotEmpty();
        boolean matched = results.stream().anyMatch(chunk -> chunk.getChunkText().contains(expectedSnippet));
        assertThat(matched).as("Expected search results for '%s' to contain '%s'", queryPhrase, expectedSnippet).isTrue();
    }
}
