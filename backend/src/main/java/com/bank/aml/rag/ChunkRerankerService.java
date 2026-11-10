package com.bank.aml.rag;

import com.bank.aml.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance Contextual Reranker for AML Compliance Retrieval.
 * Re-scores candidate policy chunks by evaluating exact regulatory code matches,
 * statutory threshold/dollar amount alignment, term coverage, and semantic vector similarity.
 */
@Service
public class ChunkRerankerService {

    private static final Logger log = LoggerFactory.getLogger(ChunkRerankerService.class);

    private static final Set<String> STOP_WORDS = Set.of(
        "a", "an", "the", "is", "are", "was", "were", "what", "how", "when", "where",
        "why", "which", "who", "whom", "this", "that", "these", "those", "and", "or",
        "but", "if", "for", "to", "in", "on", "at", "by", "from", "with", "about",
        "as", "into", "like", "through", "after", "before", "should", "would", "could",
        "can", "do", "does", "did", "i", "you", "we", "they", "it"
    );

    private static final Pattern REGULATORY_ENTITIES = Pattern.compile(
        "\\b(SAR|CTR|EDD|CDD|KYC|PEP|OFAC|FinCEN|ALRT_[A-Z0-9_]+|TM-RULE-\\d+|\\$\\d[\\d,]*|\\d+\\s*(?:days?|business days?|hours?))\\b",
        Pattern.CASE_INSENSITIVE
    );

    private final RagProperties ragProperties;

    public ChunkRerankerService(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    public List<ScoredChunk> rerank(String query, List<ScoredChunk> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        String strategy = ragProperties.getRerankerStrategy();
        if ("NONE".equalsIgnoreCase(strategy)) {
            return candidates.stream().limit(topK).toList();
        }

        // Tokenize query into significant content tokens
        List<String> queryTokens = extractQueryTokens(query);
        Set<String> queryEntities = extractEntities(query);

        double maxRrf = candidates.stream()
            .mapToDouble(c -> c.getRrfScore() != null ? c.getRrfScore() : 0.0)
            .max().orElse(1.0);
        if (maxRrf <= 0.0) maxRrf = 1.0;

        for (ScoredChunk chunk : candidates) {
            double score = scoreChunk(query, queryTokens, queryEntities, chunk, maxRrf);
            chunk.setRerankScore(score);
            chunk.setCompositeScore(score);
            if (!"HYBRID".equals(chunk.getPrimaryMatchSource())) {
                chunk.setPrimaryMatchSource("HYBRID_RERANK");
            }
        }

        // Sort descending by rerank score
        List<ScoredChunk> reranked = new ArrayList<>(candidates);
        reranked.sort((a, b) -> Double.compare(
            b.getRerankScore() != null ? b.getRerankScore() : 0.0,
            a.getRerankScore() != null ? a.getRerankScore() : 0.0
        ));

        return reranked.stream().limit(topK).toList();
    }

    private double scoreChunk(
        String rawQuery,
        List<String> queryTokens,
        Set<String> queryEntities,
        ScoredChunk chunk,
        double maxRrf
    ) {
        String chunkTextLower = chunk.getChunkText().toLowerCase();
        String sectionLower = (chunk.getSection() != null ? chunk.getSection() : "").toLowerCase();
        String titleLower = (chunk.getDocumentTitle() != null ? chunk.getDocumentTitle() : "").toLowerCase();

        // 1. Normalized RRF component (weight: 0.30)
        double normalizedRrf = (chunk.getRrfScore() != null ? chunk.getRrfScore() : 0.0) / maxRrf;

        // 2. Query Term Coverage (weight: 0.25)
        double termCoverage = 0.0;
        if (!queryTokens.isEmpty()) {
            int matchedTokens = 0;
            for (String token : queryTokens) {
                if (chunkTextLower.contains(token) || sectionLower.contains(token) || titleLower.contains(token)) {
                    matchedTokens++;
                }
            }
            termCoverage = (double) matchedTokens / queryTokens.size();
        }

        // 3. Exact Regulatory Entity / Code / Threshold Match (weight: 0.25)
        double entityMatchScore = 0.0;
        if (!queryEntities.isEmpty()) {
            int matchedEntities = 0;
            for (String entity : queryEntities) {
                if (chunkTextLower.contains(entity) || sectionLower.contains(entity)) {
                    matchedEntities++;
                }
            }
            entityMatchScore = (double) matchedEntities / queryEntities.size();
        } else {
            // If no specific entities in query, fallback to generic term coverage
            entityMatchScore = termCoverage;
        }

        // 4. Vector Cosine Similarity Component (weight: 0.20)
        double vectorSim = chunk.getVectorSimilarity() != null ? Math.max(0.0, chunk.getVectorSimilarity()) : 0.0;

        // Section header match bonus (+0.05)
        double sectionBonus = 0.0;
        if (chunk.getSection() != null && !queryTokens.isEmpty()) {
            for (String token : queryTokens) {
                if (sectionLower.contains(token)) {
                    sectionBonus = 0.05;
                    break;
                }
            }
        }

        double finalScore = (0.30 * normalizedRrf)
                          + (0.25 * termCoverage)
                          + (0.25 * entityMatchScore)
                          + (0.20 * vectorSim)
                          + sectionBonus;

        return Math.min(1.0, Math.max(0.0, finalScore));
    }

    private List<String> extractQueryTokens(String query) {
        if (query == null) return Collections.emptyList();
        String[] rawTokens = query.toLowerCase().replaceAll("[^a-z0-9\\$]", " ").split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String t : rawTokens) {
            String trimmed = t.trim();
            if (trimmed.length() > 2 && !STOP_WORDS.contains(trimmed)) {
                tokens.add(trimmed);
            }
        }
        return tokens;
    }

    private Set<String> extractEntities(String query) {
        if (query == null) return Collections.emptySet();
        Set<String> entities = new HashSet<>();
        Matcher m = REGULATORY_ENTITIES.matcher(query);
        while (m.find()) {
            entities.add(m.group().toLowerCase());
        }
        return entities;
    }
}
