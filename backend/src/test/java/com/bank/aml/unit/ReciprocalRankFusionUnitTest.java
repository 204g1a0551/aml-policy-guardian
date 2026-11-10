package com.bank.aml.unit;

import com.bank.aml.config.RagProperties;
import com.bank.aml.entity.Document;
import com.bank.aml.rag.ReciprocalRankFusionService;
import com.bank.aml.rag.ScoredChunk;
import com.bank.aml.repository.ChunkSimilarityProjection;
import com.bank.aml.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ReciprocalRankFusionUnitTest {

    private ReciprocalRankFusionService rrfService;
    private RagProperties ragProperties;
    private DocumentRepository documentRepository;

    @BeforeEach
    void setUp() {
        ragProperties = new RagProperties();
        ragProperties.setRrfK(60.0);
        ragProperties.setVectorWeight(0.6);
        ragProperties.setKeywordWeight(0.4);

        documentRepository = Mockito.mock(DocumentRepository.class);
        rrfService = new ReciprocalRankFusionService(ragProperties, documentRepository);
    }

    @Test
    @DisplayName("RRF: Boosts hybrid chunk appearing in both vector and keyword rankings")
    void testHybridChunkBoostedAboveSingleListChunks() {
        UUID docId = UUID.randomUUID();
        UUID chunk1 = UUID.randomUUID(); // only in vector
        UUID chunk2 = UUID.randomUUID(); // in BOTH vector and keyword (rank 2 in vector, rank 1 in keyword)
        UUID chunk3 = UUID.randomUUID(); // only in keyword

        List<ChunkSimilarityProjection> vectorResults = List.of(
            mockProjection(chunk1, docId, "Section 1", 0.95),
            mockProjection(chunk2, docId, "Section 2", 0.90)
        );

        List<ChunkSimilarityProjection> keywordResults = List.of(
            mockProjection(chunk2, docId, "Section 2", 0.88),
            mockProjection(chunk3, docId, "Section 3", 0.70)
        );

        Map<UUID, Document> docMap = Map.of(docId, mockDocument(docId, "AML Monitoring Policy", "PROCEDURE"));

        List<ScoredChunk> fused = rrfService.fuse(vectorResults, keywordResults, docMap);

        assertThat(fused).hasSize(3);

        // Chunk 2 appears in both lists:
        // RRF(chunk2) = 0.6/(60+2) + 0.4/(60+1) = 0.6/62 + 0.4/61 = 0.009677 + 0.006557 = 0.016234
        // RRF(chunk1) = 0.6/(60+1) = 0.6/61 = 0.009836
        // RRF(chunk3) = 0.4/(60+2) = 0.4/62 = 0.006451
        assertThat(fused.get(0).getId()).isEqualTo(chunk2);
        assertThat(fused.get(0).getPrimaryMatchSource()).isEqualTo("HYBRID");
        assertThat(fused.get(0).getRrfScore()).isGreaterThan(fused.get(1).getRrfScore());
        assertThat(fused.get(1).getId()).isEqualTo(chunk1);
        assertThat(fused.get(2).getId()).isEqualTo(chunk3);
    }

    @Test
    @DisplayName("RRF: Handles empty vector or empty keyword list gracefully")
    void testEmptyListHandling() {
        UUID docId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();

        List<ChunkSimilarityProjection> vectorResults = List.of(
            mockProjection(chunkId, docId, "Section 1", 0.92)
        );

        Map<UUID, Document> docMap = Map.of(docId, mockDocument(docId, "Policy", "POLICY"));

        List<ScoredChunk> fused = rrfService.fuse(vectorResults, Collections.emptyList(), docMap);

        assertThat(fused).hasSize(1);
        assertThat(fused.get(0).getId()).isEqualTo(chunkId);
        assertThat(fused.get(0).getPrimaryMatchSource()).isEqualTo("VECTOR");
    }

    private ChunkSimilarityProjection mockProjection(UUID id, UUID docId, String section, double sim) {
        return new ChunkSimilarityProjection() {
            @Override public UUID getId() { return id; }
            @Override public UUID getDocumentId() { return docId; }
            @Override public String getChunkText() { return "Compliance chunk text for " + section; }
            @Override public Integer getChunkIndex() { return 0; }
            @Override public Integer getPageNumber() { return 1; }
            @Override public String getSection() { return section; }
            @Override public String getMetadata() { return "{}"; }
            @Override public Double getSimilarity() { return sim; }
            @Override public Double getDistance() { return 1.0 - sim; }
        };
    }

    private Document mockDocument(UUID id, String title, String type) {
        Document d = new Document();
        d.setId(id);
        d.setTitle(title);
        d.setDocumentType(type);
        d.setStatus("READY");
        return d;
    }
}
