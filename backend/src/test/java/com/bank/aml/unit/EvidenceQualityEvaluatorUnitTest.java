package com.bank.aml.unit;

import com.bank.aml.config.RagProperties;
import com.bank.aml.dto.response.CitationResponse;
import com.bank.aml.rag.EvidenceQualityEvaluator;
import com.bank.aml.rag.ScoredChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceQualityEvaluatorUnitTest {

    private EvidenceQualityEvaluator evaluator;
    private RagProperties ragProperties;

    @BeforeEach
    void setUp() {
        ragProperties = new RagProperties();
        ragProperties.setEvidenceQualityThreshold(0.65);
        evaluator = new EvidenceQualityEvaluator(ragProperties);
    }

    @Test
    @DisplayName("Evidence Evaluator: Computes HIGH evidence confidence when multi-document support and verified citations exist")
    void testHighEvidenceQuality() {
        UUID doc1 = UUID.randomUUID();
        UUID doc2 = UUID.randomUUID();

        List<ScoredChunk> chunks = List.of(
            new ScoredChunk(UUID.randomUUID(), doc1, "Policy A", "POLICY", "Text A", 0, 1, "S1", "{}", null, false, null, 0.95, 0.5, 0.02, 0.92, "HYBRID"),
            new ScoredChunk(UUID.randomUUID(), doc2, "Policy B", "POLICY", "Text B", 1, 2, "S2", "{}", null, false, null, 0.90, 0.4, 0.018, 0.88, "HYBRID")
        );

        List<CitationResponse> citations = List.of(
            new CitationResponse(doc1, "Policy A", "S1", 1, 0.92, "Snippet A", 0.90, true, "Verified"),
            new CitationResponse(doc2, "Policy B", "S2", 2, 0.88, "Snippet B", 0.85, true, "Verified")
        );

        var assessment = evaluator.assessEvidence("Query", chunks, citations, false);

        assertThat(assessment.level()).isEqualTo("HIGH");
        assertThat(assessment.sufficient()).isTrue();
        assertThat(assessment.score()).isGreaterThanOrEqualTo(0.82);
    }

    @Test
    @DisplayName("Evidence Evaluator: Flags INSUFFICIENT evidence on guardrail refusal or missing chunks")
    void testInsufficientEvidenceOnRefusalOrEmpty() {
        var assessmentRefusal = evaluator.assessEvidence("Adversarial query", List.of(), List.of(), true);
        assertThat(assessmentRefusal.level()).isEqualTo("INSUFFICIENT");
        assertThat(assessmentRefusal.sufficient()).isFalse();
        assertThat(assessmentRefusal.score()).isEqualTo(0.0);

        var assessmentEmpty = evaluator.assessEvidence("Unrelated query", List.of(), List.of(), false);
        assertThat(assessmentEmpty.level()).isEqualTo("INSUFFICIENT");
        assertThat(assessmentEmpty.sufficient()).isFalse();
    }
}
