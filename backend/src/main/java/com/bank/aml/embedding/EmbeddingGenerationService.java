package com.bank.aml.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class EmbeddingGenerationService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingGenerationService.class);
    private static final int VECTOR_DIMENSION = 1536;

    private final EmbeddingModel embeddingModel;
    private volatile boolean fallbackModeActive = false;

    public EmbeddingGenerationService(@Autowired(required = false) EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * Generates a 1536-dimensional embedding for a single text.
     */
    public float[] generateEmbedding(String text) {
        if (embeddingModel != null && !fallbackModeActive) {
            try {
                return embeddingModel.embed(text);
            } catch (Exception e) {
                fallbackModeActive = true;
                log.warn("Remote embedding model failed or offline ({}); switching to deterministic normalized fallback vector.", e.getMessage());
            }
        }
        return generateDeterministicVector(text);
    }

    public void resetFallbackMode() {
        this.fallbackModeActive = false;
    }

    /**
     * Batch generates 1536-dimensional embeddings.
     */
    public List<float[]> generateBatchEmbeddings(List<String> texts) {
        List<float[]> results = new ArrayList<>(texts.size());
        for (String text : texts) {
            results.add(generateEmbedding(text));
        }
        return results;
    }

    /**
     * Generates a unit-normalized 1536-dimensional vector deterministically from text.
     * Uses a token-hashed dense projection so texts with high term overlap have high cosine similarity.
     * Guaranteed to produce valid floats with Euclidean norm = 1.0.
     */
    public float[] generateDeterministicVector(String text) {
        float[] vector = new float[VECTOR_DIMENSION];
        if (text == null || text.isBlank()) {
            vector[0] = 1.0f;
            return vector;
        }

        String[] tokens = text.toLowerCase().split("[^a-z0-9]+");
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            int h = Math.abs(token.hashCode());
            int idx1 = h % VECTOR_DIMENSION;
            int idx2 = Math.abs(h * 31 + 17) % VECTOR_DIMENSION;
            int idx3 = Math.abs(h * 59 + 41) % VECTOR_DIMENSION;
            vector[idx1] += 1.0f;
            vector[idx2] += 0.6f;
            vector[idx3] += 0.3f;
        }

        // L2 normalize
        double sumSquares = 0.0;
        for (float v : vector) {
            sumSquares += v * v;
        }

        float norm = (float) Math.sqrt(sumSquares);
        if (norm > 0) {
            for (int i = 0; i < VECTOR_DIMENSION; i++) {
                vector[i] /= norm;
            }
        } else {
            vector[0] = 1.0f;
        }

        return vector;
    }
}
