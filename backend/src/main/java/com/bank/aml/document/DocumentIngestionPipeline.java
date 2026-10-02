package com.bank.aml.document;

import com.bank.aml.config.RagProperties;
import com.bank.aml.document.chunking.ChunkPayload;
import com.bank.aml.document.chunking.DocumentChunker;
import com.bank.aml.document.parser.DocumentParser;
import com.bank.aml.document.parser.ExtractedDocument;
import com.bank.aml.embedding.EmbeddingGenerationService;
import com.bank.aml.entity.Document;
import com.bank.aml.entity.DocumentChunk;
import com.bank.aml.exception.DocumentProcessingException;
import com.bank.aml.rag.VectorRetrievalService;
import com.bank.aml.repository.DocumentChunkRepository;
import com.bank.aml.repository.DocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentIngestionPipeline {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionPipeline.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentStorageService documentStorageService;
    private final DocumentParser documentParser;
    private final DocumentChunker documentChunker;
    private final EmbeddingGenerationService embeddingGenerationService;
    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;

    public DocumentIngestionPipeline(
        DocumentRepository documentRepository,
        DocumentChunkRepository documentChunkRepository,
        DocumentStorageService documentStorageService,
        DocumentParser documentParser,
        DocumentChunker documentChunker,
        EmbeddingGenerationService embeddingGenerationService,
        RagProperties ragProperties,
        ObjectMapper objectMapper
    ) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.documentStorageService = documentStorageService;
        this.documentParser = documentParser;
        this.documentChunker = documentChunker;
        this.embeddingGenerationService = embeddingGenerationService;
        this.ragProperties = ragProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Executes the end-to-end ingestion pipeline:
     * Upload -> Text Extraction -> Metadata Preservation -> Chunking -> Embedding -> Vector Persistence -> READY
     */
    public void ingest(UUID documentId) {
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new DocumentProcessingException("Document not found for ingestion with ID: " + documentId));

        log.info("Starting RAG ingestion pipeline for document: {} ({})", document.getTitle(), document.getFilename());

        try {
            // Step 1: Transition status to PROCESSING
            updateStatus(document, "PROCESSING", null);

            // Step 2: Load binary from storage
            Resource resource = documentStorageService.loadAsResource(document.getStoragePath());

            // Step 3: Extract structured text and page boundaries
            ExtractedDocument extracted;
            try (InputStream is = resource.getInputStream()) {
                extracted = documentParser.parse(is, document.getFilename(), document.getMimeType());
            }

            // Step 4: Semantic chunking with configurable size and overlap
            List<ChunkPayload> chunks = documentChunker.chunk(
                extracted,
                document.getId(),
                document,
                ragProperties.getChunkSize(),
                ragProperties.getChunkOverlap()
            );

            if (chunks.isEmpty()) {
                throw new DocumentProcessingException("Document parsing yielded 0 semantic chunks.");
            }

            // Step 5: Extract chunk texts for batch embedding generation
            List<String> chunkTexts = chunks.stream().map(ChunkPayload::text).toList();

            // Step 6: Generate 1536-dimensional embeddings
            List<float[]> embeddings = embeddingGenerationService.generateBatchEmbeddings(chunkTexts);

            // Step 7: Persist chunks and vectors transactionally
            persistChunksAndComplete(document, chunks, embeddings);

            log.info("RAG ingestion pipeline completed successfully for document {}. Created {} chunks.", document.getId(), chunks.size());

        } catch (Exception ex) {
            log.error("RAG ingestion pipeline failed for document {}: {}", documentId, ex.getMessage(), ex);
            handleFailure(document, ex);
            throw new DocumentProcessingException("Document ingestion failed: " + ex.getMessage(), ex);
        }
    }

    @Transactional
    public void persistChunksAndComplete(Document document, List<ChunkPayload> chunks, List<float[]> embeddings) {
        // Clear any previous chunks to ensure idempotency and clean state
        documentChunkRepository.deleteByDocumentId(document.getId());

        List<DocumentChunk> entities = new ArrayList<>(chunks.size());

        for (int i = 0; i < chunks.size(); i++) {
            ChunkPayload payload = chunks.get(i);
            float[] embedding = embeddings.get(i);

            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocumentId(document.getId());
            chunk.setChunkIndex(payload.chunkIndex());
            chunk.setPageNumber(payload.pageNumber());
            chunk.setSection(payload.section());
            chunk.setChunkText(payload.text());

            try {
                chunk.setMetadata(objectMapper.writeValueAsString(payload.metadata()));
            } catch (Exception e) {
                chunk.setMetadata("{}");
            }

            // PostgreSQL pgvector string formatting [0.1,0.2,...]
            chunk.setEmbedding(VectorRetrievalService.formatVector(embedding));
            entities.add(chunk);
        }

        // Batch save to PostgreSQL pgvector
        documentChunkRepository.saveAll(entities);

        // Transition status to READY only after all chunks and embeddings are safely committed
        document.setStatus("READY");
        document.setErrorMessage(null);
        documentRepository.save(document);
    }

    @Transactional
    public void updateStatus(Document document, String status, String errorMsg) {
        document.setStatus(status);
        document.setErrorMessage(errorMsg);
        documentRepository.save(document);
    }

    @Transactional
    public void handleFailure(Document document, Exception ex) {
        try {
            // Avoid partial inconsistent data: clean up any chunks for this document
            documentChunkRepository.deleteByDocumentId(document.getId());
        } catch (Exception cleanupEx) {
            log.error("Failed to clean up chunks during failure recovery: {}", cleanupEx.getMessage());
        }

        // Mark document FAILED and record error information
        document.setStatus("FAILED");
        document.setErrorMessage(ex.getMessage() != null ? ex.getMessage() : "Unknown ingestion pipeline error");
        documentRepository.save(document);
    }
}
