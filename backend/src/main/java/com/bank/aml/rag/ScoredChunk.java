package com.bank.aml.rag;

import com.bank.aml.repository.ChunkSimilarityProjection;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.UUID;

/**
 * Enhanced chunk representation holding vector similarity, keyword rank,
 * Reciprocal Rank Fusion (RRF) score, reranker score, and parent-child hierarchy.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScoredChunk implements ChunkSimilarityProjection, Serializable {

    private UUID id;
    private UUID documentId;
    private String documentTitle;
    private String documentType;
    private String chunkText;
    private Integer chunkIndex;
    private Integer pageNumber;
    private String section;
    private String metadata;
    private UUID parentChunkId;
    private Boolean isParent;
    private String chunkSummary;
    private String parentChunkText;

    private Double vectorSimilarity;
    private Double keywordRank;
    private Double rrfScore;
    private Double rerankScore;
    private Double compositeScore;
    private String primaryMatchSource; // "VECTOR", "KEYWORD", "HYBRID", "HYBRID_RERANK"

    public ScoredChunk() {
    }

    @JsonCreator
    public ScoredChunk(
        @JsonProperty("id") UUID id,
        @JsonProperty("documentId") UUID documentId,
        @JsonProperty("documentTitle") String documentTitle,
        @JsonProperty("documentType") String documentType,
        @JsonProperty("chunkText") String chunkText,
        @JsonProperty("chunkIndex") Integer chunkIndex,
        @JsonProperty("pageNumber") Integer pageNumber,
        @JsonProperty("section") String section,
        @JsonProperty("metadata") String metadata,
        @JsonProperty("parentChunkId") UUID parentChunkId,
        @JsonProperty("isParent") Boolean isParent,
        @JsonProperty("chunkSummary") String chunkSummary,
        @JsonProperty("vectorSimilarity") Double vectorSimilarity,
        @JsonProperty("keywordRank") Double keywordRank,
        @JsonProperty("rrfScore") Double rrfScore,
        @JsonProperty("rerankScore") Double rerankScore,
        @JsonProperty("primaryMatchSource") String primaryMatchSource
    ) {
        this.id = id;
        this.documentId = documentId;
        this.documentTitle = documentTitle != null ? documentTitle : "AML Policy Document";
        this.documentType = documentType != null ? documentType : "POLICY";
        this.chunkText = chunkText;
        this.chunkIndex = chunkIndex;
        this.pageNumber = pageNumber;
        this.section = section;
        this.metadata = metadata;
        this.parentChunkId = parentChunkId;
        this.isParent = isParent != null ? isParent : false;
        this.chunkSummary = chunkSummary;
        this.vectorSimilarity = vectorSimilarity;
        this.keywordRank = keywordRank;
        this.rrfScore = rrfScore;
        this.rerankScore = rerankScore;
        this.primaryMatchSource = primaryMatchSource;
        this.compositeScore = rerankScore != null ? rerankScore : (rrfScore != null ? rrfScore : vectorSimilarity);
    }

    public static ScoredChunk fromProjection(ChunkSimilarityProjection p, String docTitle, String docType, String matchSource) {
        return new ScoredChunk(
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
            p.getSimilarity(),
            0.0,
            0.0,
            p.getSimilarity(),
            matchSource
        );
    }

    @Override public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    @Override public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }
    public String getDocumentTitle() { return documentTitle; }
    public void setDocumentTitle(String documentTitle) { this.documentTitle = documentTitle; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    @Override public String getChunkText() { return chunkText; }
    public void setChunkText(String chunkText) { this.chunkText = chunkText; }
    @Override public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    @Override public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }
    @Override public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    @Override public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    @Override public Double getSimilarity() { return compositeScore != null ? compositeScore : vectorSimilarity; }
    @Override public Double getDistance() { return 1.0 - (vectorSimilarity != null ? vectorSimilarity : 0.0); }

    @Override public UUID getParentChunkId() { return parentChunkId; }
    public void setParentChunkId(UUID parentChunkId) { this.parentChunkId = parentChunkId; }
    @Override public Boolean getIsParent() { return isParent; }
    public void setIsParent(Boolean isParent) { this.isParent = isParent; }
    @Override public String getChunkSummary() { return chunkSummary; }
    public void setChunkSummary(String chunkSummary) { this.chunkSummary = chunkSummary; }
    @Override public Double getRank() { return keywordRank; }

    public String getParentChunkText() { return parentChunkText; }
    public void setParentChunkText(String parentChunkText) { this.parentChunkText = parentChunkText; }

    public Double getVectorSimilarity() { return vectorSimilarity; }
    public void setVectorSimilarity(Double vectorSimilarity) { this.vectorSimilarity = vectorSimilarity; }

    public Double getKeywordRank() { return keywordRank; }
    public void setKeywordRank(Double keywordRank) { this.keywordRank = keywordRank; }

    public Double getRrfScore() { return rrfScore; }
    public void setRrfScore(Double rrfScore) { this.rrfScore = rrfScore; }

    public Double getRerankScore() { return rerankScore; }
    public void setRerankScore(Double rerankScore) { this.rerankScore = rerankScore; }

    public Double getCompositeScore() { return compositeScore; }
    public void setCompositeScore(Double compositeScore) { this.compositeScore = compositeScore; }

    public String getPrimaryMatchSource() { return primaryMatchSource; }
    public void setPrimaryMatchSource(String primaryMatchSource) { this.primaryMatchSource = primaryMatchSource; }
}
