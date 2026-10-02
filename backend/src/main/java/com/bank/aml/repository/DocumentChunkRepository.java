package com.bank.aml.repository;

import com.bank.aml.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(UUID documentId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    void deleteByDocumentId(UUID documentId);

    long countByDocumentId(UUID documentId);

    @Query(value = """
        SELECT c.id AS id,
               c.document_id AS documentId,
               c.chunk_text AS chunkText,
               c.chunk_index AS chunkIndex,
               c.page_number AS pageNumber,
               c.section AS section,
               c.metadata AS metadata,
               (1 - (c.embedding <=> CAST(:embedding AS vector))) AS similarity,
               (c.embedding <=> CAST(:embedding AS vector)) AS distance
        FROM document_chunks c
        WHERE (1 - (c.embedding <=> CAST(:embedding AS vector))) >= :minSimilarity
        ORDER BY c.embedding <=> CAST(:embedding AS vector) ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<ChunkSimilarityProjection> findSimilarChunks(
        @Param("embedding") String embedding,
        @Param("minSimilarity") double minSimilarity,
        @Param("limit") int limit
    );
}
