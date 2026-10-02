package com.bank.aml.rag;

import com.bank.aml.repository.ChunkSimilarityProjection;
import com.bank.aml.repository.DocumentChunkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class VectorRetrievalService {

    private final DocumentChunkRepository documentChunkRepository;

    public VectorRetrievalService(DocumentChunkRepository documentChunkRepository) {
        this.documentChunkRepository = documentChunkRepository;
    }

    /**
     * Executes vector similarity search against PostgreSQL pgvector using cosine distance.
     *
     * @param queryEmbedding float array representing the 1536-dimensional query embedding
     * @param minSimilarity minimum cosine similarity threshold (e.g. 0.72)
     * @param limit maximum number of chunks to return (top-K)
     * @return List of matching chunk projections sorted by similarity descending
     */
    public List<ChunkSimilarityProjection> searchSimilarChunks(float[] queryEmbedding, double minSimilarity, int limit) {
        String vectorString = formatVector(queryEmbedding);
        return searchSimilarChunks(vectorString, minSimilarity, limit);
    }

    /**
     * Executes vector similarity search given an already formatted vector string.
     */
    public List<ChunkSimilarityProjection> searchSimilarChunks(String vectorString, double minSimilarity, int limit) {
        return documentChunkRepository.findSimilarChunks(vectorString, minSimilarity, limit);
    }

    /**
     * Formats float array into PostgreSQL pgvector format: [0.025,0.025,...]
     */
    public static String formatVector(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
