package com.bank.aml.rag;

import com.bank.aml.config.RagProperties;
import com.bank.aml.entity.Document;
import com.bank.aml.repository.ChunkSimilarityProjection;
import com.bank.aml.repository.DocumentRepository;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Reciprocal Rank Fusion (RRF) Service.
 * Fuses ranked retrieval results from multiple sources (pgvector dense search + PostgreSQL full-text search)
 * into a single unified ranked candidate list using the standard RRF formula:
 * RRF_score(d) = SUM_m [ w_m / (k + rank_m(d)) ]
 */
@Service
public class ReciprocalRankFusionService {

    private final RagProperties ragProperties;
    private final DocumentRepository documentRepository;

    public ReciprocalRankFusionService(RagProperties ragProperties, DocumentRepository documentRepository) {
        this.ragProperties = ragProperties;
        this.documentRepository = documentRepository;
    }

    public List<ScoredChunk> fuse(
        List<ChunkSimilarityProjection> vectorResults,
        List<ChunkSimilarityProjection> keywordResults,
        Map<UUID, Document> documentsMap
    ) {
        double k = ragProperties.getRrfK();
        double wVector = ragProperties.getVectorWeight();
        double wKeyword = ragProperties.getKeywordWeight();

        Map<UUID, ScoredChunk> chunkMap = new HashMap<>();

        // 1. Process Vector Search Results
        for (int rank = 0; rank < vectorResults.size(); rank++) {
            ChunkSimilarityProjection p = vectorResults.get(rank);
            UUID id = p.getId();
            Document doc = documentsMap.get(p.getDocumentId());
            String docTitle = doc != null ? doc.getTitle() : "AML Compliance Document";
            String docType = doc != null ? doc.getDocumentType() : "POLICY";

            double vectorScore = p.getSimilarity() != null ? p.getSimilarity() : 0.0;
            double rrfContribution = wVector / (k + (rank + 1));

            ScoredChunk scored = new ScoredChunk(
                p.getId(),
                p.getDocumentId(),
                docTitle,
                docType,
                p.getChunkText(),
                p.getChunkIndex(),
                p.getPageNumber(),
                p.getSection(),
                p.getMetadata(),
                p.getParentChunkId(),
                p.getIsParent(),
                p.getChunkSummary(),
                vectorScore,
                0.0,
                rrfContribution,
                null,
                "VECTOR"
            );
            chunkMap.put(id, scored);
        }

        // 2. Process Keyword Full-Text Search Results
        for (int rank = 0; rank < keywordResults.size(); rank++) {
            ChunkSimilarityProjection p = keywordResults.get(rank);
            UUID id = p.getId();
            double keywordRank = p.getSimilarity() != null ? p.getSimilarity() : 0.0;
            double rrfContribution = wKeyword / (k + (rank + 1));

            if (chunkMap.containsKey(id)) {
                // In both vector and keyword search: HYBRID MATCH!
                ScoredChunk existing = chunkMap.get(id);
                existing.setKeywordRank(keywordRank);
                existing.setRrfScore(existing.getRrfScore() + rrfContribution);
                existing.setCompositeScore(existing.getRrfScore());
                existing.setPrimaryMatchSource("HYBRID");
            } else {
                Document doc = documentsMap.get(p.getDocumentId());
                String docTitle = doc != null ? doc.getTitle() : "AML Compliance Document";
                String docType = doc != null ? doc.getDocumentType() : "POLICY";

                ScoredChunk scored = new ScoredChunk(
                    p.getId(),
                    p.getDocumentId(),
                    docTitle,
                    docType,
                    p.getChunkText(),
                    p.getChunkIndex(),
                    p.getPageNumber(),
                    p.getSection(),
                    p.getMetadata(),
                    p.getParentChunkId(),
                    p.getIsParent(),
                    p.getChunkSummary(),
                    0.0,
                    keywordRank,
                    rrfContribution,
                    null,
                    "KEYWORD"
                );
                chunkMap.put(id, scored);
            }
        }

        // 3. Sort by RRF score descending
        List<ScoredChunk> fused = new ArrayList<>(chunkMap.values());
        fused.sort((a, b) -> Double.compare(
            b.getRrfScore() != null ? b.getRrfScore() : 0.0,
            a.getRrfScore() != null ? a.getRrfScore() : 0.0
        ));

        return fused;
    }
}
