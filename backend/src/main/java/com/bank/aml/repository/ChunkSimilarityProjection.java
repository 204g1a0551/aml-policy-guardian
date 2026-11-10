package com.bank.aml.repository;

import java.util.UUID;

public interface ChunkSimilarityProjection {
    UUID getId();
    UUID getDocumentId();
    String getChunkText();
    Integer getChunkIndex();
    Integer getPageNumber();
    String getSection();
    String getMetadata();
    Double getSimilarity();
    Double getDistance();

    default UUID getParentChunkId() { return null; }
    default Boolean getIsParent() { return false; }
    default String getChunkSummary() { return null; }
    default Double getRank() { return getSimilarity(); }
    default String getPolicyCode() { return null; }
    default Boolean getIsEffective() { return true; }
}
