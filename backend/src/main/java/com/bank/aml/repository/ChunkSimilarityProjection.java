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
}
