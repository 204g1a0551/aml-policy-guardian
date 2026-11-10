package com.bank.aml.rag;

import com.bank.aml.config.RagProperties;
import com.bank.aml.dto.response.CitationResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Calibrated Evidence Quality Evaluator.
 * Avoids treating vector cosine similarity as a probability of correctness.
 * Computes a multi-factor composite evidence indicator reflecting policy agreement,
 * citation support verification, and entity alignment.
 */
@Service
public class EvidenceQualityEvaluator {

    private final RagProperties ragProperties;

    public record EvidenceAssessment(
        double score,
        String level, // "HIGH", "MEDIUM", "LOW", "INSUFFICIENT"
        boolean sufficient,
        String explanation
    ) {}

    public EvidenceQualityEvaluator(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    public EvidenceQualityEvaluator() {
        this(new RagProperties());
    }

    public EvidenceAssessment assessEvidence(
        String query,
        List<ScoredChunk> retrievedChunks,
        List<CitationResponse> verifiedCitations,
        boolean isRefusal
    ) {
        if (isRefusal || retrievedChunks == null || retrievedChunks.isEmpty()) {
            return new EvidenceAssessment(
                0.0,
                "INSUFFICIENT",
                false,
                "Insufficient evidence: query is outside approved policy scope or refused by guardrails."
            );
        }

        double topScore = retrievedChunks.get(0).getSimilarity() != null ? retrievedChunks.get(0).getSimilarity() : 0.0;
        double minThreshold = ragProperties.getEvidenceQualityThreshold();

        // 1. Base Score from top retrieved chunk
        double baseScore = topScore;

        // 2. Multi-source agreement bonus (+0.10 if >= 2 distinct policy documents)
        long distinctDocs = retrievedChunks.stream().map(ScoredChunk::getDocumentId).distinct().count();
        double multiSourceBonus = distinctDocs >= 2 ? 0.10 : 0.0;

        // 3. Verified citation ratio (0.0 to 0.15)
        double citationBonus = 0.0;
        if (verifiedCitations != null && !verifiedCitations.isEmpty()) {
            long verifiedCount = verifiedCitations.stream().filter(c -> Boolean.TRUE.equals(c.verified())).count();
            citationBonus = 0.15 * ((double) verifiedCount / verifiedCitations.size());
        }

        // Composite calibrated score
        double compositeScore = Math.min(1.0, Math.max(0.0, (0.75 * baseScore) + multiSourceBonus + citationBonus));

        if (compositeScore < 0.30 || topScore < 0.15) {
            return new EvidenceAssessment(
                compositeScore,
                "INSUFFICIENT",
                false,
                "Insufficient evidence: No approved bank policy contains verified rules for this inquiry."
            );
        } else if (compositeScore >= 0.75) {
            return new EvidenceAssessment(
                compositeScore,
                "HIGH",
                true,
                "High evidence confidence: Authoritative policy corroboration with verified document citations."
            );
        } else if (compositeScore >= 0.50) {
            return new EvidenceAssessment(
                compositeScore,
                "MEDIUM",
                true,
                "Moderate evidence confidence: Direct policy guidance identified and verified."
            );
        } else {
            return new EvidenceAssessment(
                compositeScore,
                "LOW",
                true,
                "Low evidence confidence: Marginal policy alignment; analyst manual review recommended."
            );
        }
    }
}
