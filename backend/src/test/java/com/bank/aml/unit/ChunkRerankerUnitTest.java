package com.bank.aml.unit;

import com.bank.aml.config.RagProperties;
import com.bank.aml.rag.ChunkRerankerService;
import com.bank.aml.rag.ScoredChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkRerankerUnitTest {

    private ChunkRerankerService rerankerService;
    private RagProperties ragProperties;

    @BeforeEach
    void setUp() {
        ragProperties = new RagProperties();
        ragProperties.setRerankerStrategy("LEXICAL_SEMANTIC_CROSS_ENCODER");
        rerankerService = new ChunkRerankerService(ragProperties);
    }

    @Test
    @DisplayName("Reranker: Promotes chunk with exact regulatory code and threshold match to Rank 1")
    void testExactRegulatoryCodeAndThresholdPromotion() {
        UUID docId = UUID.randomUUID();

        // Chunk A: Generic statement about monitoring
        ScoredChunk chunkA = new ScoredChunk(
            UUID.randomUUID(), docId, "AML Monitoring Guide", "POLICY",
            "General transaction monitoring guidelines apply to all retail and corporate deposit transactions.",
            0, 1, "Section 1: Overview", "{}", null, false, null,
            0.92, 0.20, 0.015, null, "VECTOR"
        );

        // Chunk B: Exact match for scenario ALRT_HVT_01 and $10,000 threshold
        ScoredChunk chunkB = new ScoredChunk(
            UUID.randomUUID(), docId, "High-Value Transaction Procedure", "PROCEDURE",
            "Scenario ALRT_HVT_01 triggers whenever a customer initiates an aggregate cash deposit exceeding $10,000. Analyst must file CTR within 15 days.",
            1, 2, "Section 2: High-Value Thresholds", "{}", null, false, null,
            0.88, 0.40, 0.012, null, "HYBRID"
        );

        String query = "What are the rules for alert ALRT_HVT_01 and $10,000 cash deposits?";

        List<ScoredChunk> reranked = rerankerService.rerank(query, List.of(chunkA, chunkB), 2);

        assertThat(reranked).hasSize(2);
        // Chunk B should be promoted to #1 because of exact entity/code/amount matches
        assertThat(reranked.get(0).getId()).isEqualTo(chunkB.getId());
        assertThat(reranked.get(0).getRerankScore()).isGreaterThan(reranked.get(1).getRerankScore());
        assertThat(reranked.get(0).getChunkText()).contains("ALRT_HVT_01");
    }

    @Test
    @DisplayName("Reranker: When strategy is NONE, preserves incoming candidate order")
    void testStrategyNonePreservesOrder() {
        ragProperties.setRerankerStrategy("NONE");
        UUID docId = UUID.randomUUID();

        ScoredChunk c1 = new ScoredChunk(UUID.randomUUID(), docId, "Doc 1", "P", "Text 1", 0, 1, "S1", "{}", null, false, null, 0.9, 0.0, 0.02, null, "VECTOR");
        ScoredChunk c2 = new ScoredChunk(UUID.randomUUID(), docId, "Doc 2", "P", "Text 2", 1, 1, "S2", "{}", null, false, null, 0.8, 0.0, 0.01, null, "VECTOR");

        List<ScoredChunk> result = rerankerService.rerank("Some query", List.of(c1, c2), 2);

        assertThat(result.get(0).getId()).isEqualTo(c1.getId());
        assertThat(result.get(1).getId()).isEqualTo(c2.getId());
    }
}
