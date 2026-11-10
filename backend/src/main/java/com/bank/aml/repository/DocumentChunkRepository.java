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
               c.parent_chunk_id AS parentChunkId,
               c.is_parent AS isParent,
               c.chunk_summary AS chunkSummary,
               (1 - (c.embedding <=> CAST(:embedding AS vector))) AS similarity,
               (c.embedding <=> CAST(:embedding AS vector)) AS distance
        FROM document_chunks c
        JOIN documents d ON d.id = c.document_id
        WHERE d.status IN ('ACTIVE', 'READY')
          AND (CAST(:tenantId AS varchar) IS NULL OR (c.tenant_id = CAST(:tenantId AS varchar) AND d.tenant_id = CAST(:tenantId AS varchar)))
          AND (1 - (c.embedding <=> CAST(:embedding AS vector))) >= :minSimilarity
        ORDER BY c.embedding <=> CAST(:embedding AS vector) ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<ChunkSimilarityProjection> findSimilarChunks(
        @Param("embedding") String embedding,
        @Param("minSimilarity") double minSimilarity,
        @Param("tenantId") String tenantId,
        @Param("limit") int limit
    );

    default List<ChunkSimilarityProjection> findSimilarChunks(String embedding, double minSimilarity, int limit) {
        return findSimilarChunks(embedding, minSimilarity, null, limit);
    }

    @Query(value = """
        SELECT c.id AS id,
               c.document_id AS documentId,
               c.chunk_text AS chunkText,
               c.chunk_index AS chunkIndex,
               c.page_number AS pageNumber,
               c.section AS section,
               c.metadata AS metadata,
               c.parent_chunk_id AS parentChunkId,
               c.is_parent AS isParent,
               c.chunk_summary AS chunkSummary,
               (1 - (c.embedding <=> CAST(:embedding AS vector))) AS similarity,
               (c.embedding <=> CAST(:embedding AS vector)) AS distance
        FROM document_chunks c
        JOIN documents d ON d.id = c.document_id
        WHERE d.status IN ('ACTIVE', 'READY')
          AND (CAST(:tenantId AS varchar) IS NULL OR (c.tenant_id = CAST(:tenantId AS varchar) AND d.tenant_id = CAST(:tenantId AS varchar)))
          AND (CAST(:documentId AS uuid) IS NULL OR c.document_id = CAST(:documentId AS uuid))
          AND (CAST(:documentType AS varchar) IS NULL OR d.document_type = CAST(:documentType AS varchar))
          AND (CAST(:section AS varchar) IS NULL OR lower(c.section) LIKE lower(concat('%', CAST(:section AS varchar), '%')))
          AND (1 - (c.embedding <=> CAST(:embedding AS vector))) >= :minSimilarity
        ORDER BY c.embedding <=> CAST(:embedding AS vector) ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<ChunkSimilarityProjection> findSimilarChunksWithFilters(
        @Param("embedding") String embedding,
        @Param("minSimilarity") double minSimilarity,
        @Param("tenantId") String tenantId,
        @Param("documentId") UUID documentId,
        @Param("documentType") String documentType,
        @Param("section") String section,
        @Param("limit") int limit
    );

    default List<ChunkSimilarityProjection> findSimilarChunksWithFilters(
        String embedding,
        double minSimilarity,
        UUID documentId,
        String documentType,
        String section,
        int limit
    ) {
        return findSimilarChunksWithFilters(embedding, minSimilarity, null, documentId, documentType, section, limit);
    }

    @Query(value = """
        SELECT c.id AS id,
               c.document_id AS documentId,
               c.chunk_text AS chunkText,
               c.chunk_index AS chunkIndex,
               c.page_number AS pageNumber,
               c.section AS section,
               c.metadata AS metadata,
               c.parent_chunk_id AS parentChunkId,
               c.is_parent AS isParent,
               c.chunk_summary AS chunkSummary,
               ts_rank(c.text_search_tsv, plainto_tsquery('english', :query)) AS similarity,
               0.0 AS distance
        FROM document_chunks c
        JOIN documents d ON d.id = c.document_id
        WHERE d.status IN ('ACTIVE', 'READY')
          AND (CAST(:tenantId AS varchar) IS NULL OR (c.tenant_id = CAST(:tenantId AS varchar) AND d.tenant_id = CAST(:tenantId AS varchar)))
          AND (CAST(:documentId AS uuid) IS NULL OR c.document_id = CAST(:documentId AS uuid))
          AND (CAST(:documentType AS varchar) IS NULL OR d.document_type = CAST(:documentType AS varchar))
          AND (CAST(:section AS varchar) IS NULL OR lower(c.section) LIKE lower(concat('%', CAST(:section AS varchar), '%')))
          AND c.text_search_tsv @@ plainto_tsquery('english', :query)
        ORDER BY ts_rank(c.text_search_tsv, plainto_tsquery('english', :query)) DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ChunkSimilarityProjection> findChunksByKeywordSearch(
        @Param("query") String query,
        @Param("tenantId") String tenantId,
        @Param("documentId") UUID documentId,
        @Param("documentType") String documentType,
        @Param("section") String section,
        @Param("limit") int limit
    );

    default List<ChunkSimilarityProjection> findChunksByKeywordSearch(
        String query,
        UUID documentId,
        String documentType,
        String section,
        int limit
    ) {
        return findChunksByKeywordSearch(query, null, documentId, documentType, section, limit);
    }

    @Query(value = """
        SELECT c.* FROM document_chunks c
        WHERE c.id IN (:ids)
        """, nativeQuery = true)
    List<DocumentChunk> findAllByIds(@Param("ids") List<UUID> ids);
}
