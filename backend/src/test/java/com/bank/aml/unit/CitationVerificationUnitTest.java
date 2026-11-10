package com.bank.aml.unit;

import com.bank.aml.dto.response.CitationResponse;
import com.bank.aml.entity.Document;
import com.bank.aml.rag.CitationVerificationService;
import com.bank.aml.rag.ScoredChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CitationVerificationUnitTest {

    private CitationVerificationService verificationService;

    @BeforeEach
    void setUp() {
        verificationService = new CitationVerificationService();
    }

    @Test
    @DisplayName("Citation Verification: Verifies claims corroborated by authoritative chunk text")
    void testCorroboratedCitationVerified() {
        UUID docId = UUID.randomUUID();
        Document doc = new Document();
        doc.setId(docId);
        doc.setTitle("Transaction Monitoring Procedure");
        doc.setDocumentType("PROCEDURE");

        ScoredChunk chunk = new ScoredChunk(
            UUID.randomUUID(), docId, "Transaction Monitoring Procedure", "PROCEDURE",
            "Under Section 4.2, the mandatory SAR filing deadline is 30 calendar days from initial alert determination. If no suspect is identified, the deadline extends to 60 days.",
            0, 14, "Section 4.2: SAR Deadlines", "{}", null, false, null,
            0.92, 0.40, 0.018, 0.89, "HYBRID_RERANK"
        );

        String answer = "According to Transaction Monitoring Procedure Section 4.2, the mandatory SAR filing deadline is 30 calendar days from initial alert determination.";

        List<CitationResponse> citations = verificationService.verifyAndEnrichCitations(
            answer, List.of(chunk), Map.of(docId, doc)
        );

        assertThat(citations).hasSize(1);
        CitationResponse c = citations.get(0);
        assertThat(c.verified()).isTrue();
        assertThat(c.citationSupportScore()).isGreaterThan(0.35);
        assertThat(c.snippet()).contains("mandatory SAR filing deadline is 30 calendar days");
        assertThat(c.documentTitle()).isEqualTo("Transaction Monitoring Procedure");
        assertThat(c.section()).isEqualTo("Section 4.2: SAR Deadlines");
        assertThat(c.pageNumber()).isEqualTo(14);
    }

    @Test
    @DisplayName("Citation Verification: Flags unverified citations when answer lacks corroboration")
    void testUnverifiedCitationFlagged() {
        UUID docId = UUID.randomUUID();
        Document doc = new Document();
        doc.setId(docId);
        doc.setTitle("Unrelated Account Policy");
        doc.setDocumentType("POLICY");

        ScoredChunk chunk = new ScoredChunk(
            UUID.randomUUID(), docId, "Unrelated Account Policy", "POLICY",
            "Cheque deposit slips must be signed by the account holder in blue ink.",
            0, 1, "Section 1", "{}", null, false, null,
            0.50, 0.0, 0.005, 0.30, "VECTOR"
        );

        String answer = "Wire transfers exceeding one million dollars must be escalated to the Executive Risk Committee immediately.";

        List<CitationResponse> citations = verificationService.verifyAndEnrichCitations(
            answer, List.of(chunk), Map.of(docId, doc)
        );

        assertThat(citations).hasSize(1);
        CitationResponse c = citations.get(0);
        assertThat(c.verified()).isFalse();
    }
}
