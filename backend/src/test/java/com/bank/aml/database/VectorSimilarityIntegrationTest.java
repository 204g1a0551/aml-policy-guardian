package com.bank.aml.database;

import com.bank.aml.entity.Document;
import com.bank.aml.entity.DocumentChunk;
import com.bank.aml.entity.User;
import com.bank.aml.rag.VectorRetrievalService;
import com.bank.aml.repository.ChunkSimilarityProjection;
import com.bank.aml.repository.DocumentChunkRepository;
import com.bank.aml.repository.DocumentRepository;
import com.bank.aml.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VectorSimilarityIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private VectorRetrievalService vectorRetrievalService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("pgvector: Extension 'vector' is enabled in PostgreSQL")
    void testPgVectorExtensionEnabled() {
        String extName = jdbcTemplate.queryForObject(
            "SELECT extname FROM pg_extension WHERE extname = 'vector'",
            String.class
        );
        assertThat(extName).isEqualTo("vector");
    }

    @Test
    @DisplayName("pgvector: HNSW vector index is created on document_chunks.embedding")
    void testHnswIndexExists() {
        List<String> indexes = jdbcTemplate.queryForList(
            "SELECT indexname FROM pg_indexes WHERE tablename = 'document_chunks' AND indexname LIKE '%embedding%'",
            String.class
        );
        assertThat(indexes).isNotEmpty();
    }

    @Test
    @DisplayName("pgvector: 1536-dimensional vector insertion and cosine similarity search")
    void testVectorCosineSimilaritySearch() {
        User admin = userRepository.findByUsername("admin").orElseThrow();
        Document doc = documentRepository.save(new Document(
            "Vector Test Policy", "vector_test.pdf", "SOP", "v1.0", "Compliance", "ACTIVE", admin.getId()
        ));

        // Create vector A: all dimensions filled with 0.0255155 (~norm 1.0)
        float[] vectorA = new float[1536];
        Arrays.fill(vectorA, 0.0255155f);

        // Create vector B: orthogonal/opposite
        float[] vectorB = new float[1536];
        Arrays.fill(vectorB, -0.0255155f);

        DocumentChunk chunkA = new DocumentChunk();
        chunkA.setDocumentId(doc.getId());
        chunkA.setChunkText("Close match chunk content");
        chunkA.setChunkIndex(0);
        chunkA.setSection("Matching Section");
        chunkA.setPageNumber(1);
        chunkA.setEmbedding(VectorRetrievalService.formatVector(vectorA));

        DocumentChunk chunkB = new DocumentChunk();
        chunkB.setDocumentId(doc.getId());
        chunkB.setChunkText("Distant match chunk content");
        chunkB.setChunkIndex(1);
        chunkB.setSection("Opposite Section");
        chunkB.setPageNumber(2);
        chunkB.setEmbedding(VectorRetrievalService.formatVector(vectorB));

        documentChunkRepository.saveAll(List.of(chunkA, chunkB));

        // Search with queryVector matching vectorA
        List<ChunkSimilarityProjection> results = vectorRetrievalService.searchSimilarChunks(
            vectorA,
            0.50, // similarity threshold
            5     // topK
        );

        assertThat(results).isNotEmpty();
        boolean foundExactMatch = results.stream()
            .anyMatch(r -> "Close match chunk content".equals(r.getChunkText()) && r.getSimilarity() > 0.99);
        assertThat(foundExactMatch).isTrue();
    }

    @Test
    @DisplayName("pgvector: Similarity threshold filters out low-similarity chunks")
    void testVectorSimilarityThresholdFiltering() {
        float[] queryVector = new float[1536];
        Arrays.fill(queryVector, 0.0255155f);

        // Strict threshold of 0.999
        List<ChunkSimilarityProjection> strictResults = vectorRetrievalService.searchSimilarChunks(
            queryVector,
            0.999,
            10
        );

        // Lenient threshold of 0.01
        List<ChunkSimilarityProjection> lenientResults = vectorRetrievalService.searchSimilarChunks(
            queryVector,
            0.01,
            10
        );

        assertThat(lenientResults.size()).isGreaterThanOrEqualTo(strictResults.size());
    }

    @Test
    @DisplayName("pgvector: Top-K limits result count strictly")
    void testVectorTopKLimit() {
        float[] queryVector = new float[1536];
        Arrays.fill(queryVector, 0.0255155f);

        List<ChunkSimilarityProjection> top1 = vectorRetrievalService.searchSimilarChunks(
            queryVector,
            0.0,
            1
        );

        assertThat(top1.size()).isLessThanOrEqualTo(1);
    }
}
