package com.bank.aml.security.rag;

import com.bank.aml.config.RagProperties;
import com.bank.aml.dto.request.RetrievalFilterDto;
import com.bank.aml.embedding.EmbeddingGenerationService;
import com.bank.aml.entity.ChatSession;
import com.bank.aml.entity.Document;
import com.bank.aml.entity.DocumentChunk;
import com.bank.aml.exception.ResourceNotFoundException;
import com.bank.aml.rag.*;
import com.bank.aml.repository.ChatSessionRepository;
import com.bank.aml.repository.ChunkSimilarityProjection;
import com.bank.aml.repository.DocumentRepository;
import com.bank.aml.security.SecurityUserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AuthorizedRetrievalService {

    private final VectorRetrievalService vectorRetrievalService;
    private final EmbeddingGenerationService embeddingGenerationService;
    private final DocumentRepository documentRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final SecurityAuditService securityAuditService;
    private final RagProperties ragProperties;
    private final ReciprocalRankFusionService reciprocalRankFusionService;
    private final ChunkRerankerService chunkRerankerService;
    private final QueryExpansionService queryExpansionService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.bank.aml.observability.RagObservabilityService observabilityService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.bank.aml.cache.TenantAwareCacheService cacheService;

    @org.springframework.beans.factory.annotation.Autowired
    public AuthorizedRetrievalService(
        VectorRetrievalService vectorRetrievalService,
        EmbeddingGenerationService embeddingGenerationService,
        DocumentRepository documentRepository,
        ChatSessionRepository chatSessionRepository,
        SecurityAuditService securityAuditService,
        RagProperties ragProperties,
        ReciprocalRankFusionService reciprocalRankFusionService,
        ChunkRerankerService chunkRerankerService,
        QueryExpansionService queryExpansionService
    ) {
        this.vectorRetrievalService = vectorRetrievalService;
        this.embeddingGenerationService = embeddingGenerationService;
        this.documentRepository = documentRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.securityAuditService = securityAuditService;
        this.ragProperties = ragProperties;
        this.reciprocalRankFusionService = reciprocalRankFusionService;
        this.chunkRerankerService = chunkRerankerService;
        this.queryExpansionService = queryExpansionService;
    }

    public AuthorizedRetrievalService(
        VectorRetrievalService vectorRetrievalService,
        EmbeddingGenerationService embeddingGenerationService,
        DocumentRepository documentRepository,
        ChatSessionRepository chatSessionRepository,
        SecurityAuditService securityAuditService,
        RagProperties ragProperties
    ) {
        this(
            vectorRetrievalService,
            embeddingGenerationService,
            documentRepository,
            chatSessionRepository,
            securityAuditService,
            ragProperties,
            new ReciprocalRankFusionService(ragProperties, documentRepository),
            new ChunkRerankerService(ragProperties),
            new QueryExpansionService(ragProperties)
        );
    }

    /**
     * Verifies that the authenticated user owns the chat session before allowing query or retrieval.
     * Prevents cross-user context leakage and IDOR vulnerabilities.
     */
    public ChatSession validateSessionAccess(UUID sessionId, SecurityUserPrincipal user) {
        ChatSession session = chatSessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("Chat session not found with ID: " + sessionId));

        String userTenant = user.getTenantId() != null ? user.getTenantId() : "default-bank";
        String sessionTenant = session.getTenantId() != null ? session.getTenantId() : "default-bank";
        if (!userTenant.equals(sessionTenant)) {
            securityAuditService.recordSecurityEvent(
                user.getId(),
                userTenant,
                "CROSS_TENANT_ACCESS_DENIED",
                "CHAT_SESSION",
                sessionId.toString(),
                UUID.randomUUID().toString(),
                Map.of("sessionTenant", sessionTenant, "userTenant", userTenant, "requestingUserId", user.getId().toString())
            );
            throw new AccessDeniedException("Access denied: Cross-tenant session access prohibited.");
        }

        boolean isAdmin = user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!session.getUserId().equals(user.getId()) && !isAdmin) {
            securityAuditService.recordSecurityEvent(
                user.getId(),
                "CROSS_USER_ACCESS_DENIED",
                "CHAT_SESSION",
                sessionId.toString(),
                UUID.randomUUID().toString(),
                Map.of("targetSessionUserId", session.getUserId().toString(), "requestingUserId", user.getId().toString())
            );
            throw new AccessDeniedException("Access denied: You do not have permission to view or interact with this chat session.");
        }

        return session;
    }

    /**
     * Backward-compatible overload without explicit filter parameter.
     */
    public List<ScoredChunk> retrieveAuthorizedContext(String query, SecurityUserPrincipal user) {
        return retrieveAuthorizedContext(query, user, null);
    }

    /**
     * Executes enterprise hybrid retrieval:
     * 1. Multi-Query Expansion
     * 2. pgvector Dense Vector Search (with metadata pre-filters)
     * 3. PostgreSQL tsvector Keyword Full-Text Search (with metadata pre-filters)
     * 4. Reciprocal Rank Fusion (RRF) deduplication & rank aggregation
     * 5. Contextual Reranking (lexical-semantic cross-scoring)
     * 6. Parent-Child Chunk Context Resolution
     */
    public List<ScoredChunk> retrieveAuthorizedContext(
        String query,
        SecurityUserPrincipal user,
        RetrievalFilterDto filters
    ) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        // Determine retrieval strategy (from filters, or default from properties)
        RetrievalStrategy strategy = resolveStrategy(filters);
        long startRetrieval = System.nanoTime();

        // Check tenant-aware cache for repeated retrieval
        String cacheKey = query.trim().toLowerCase() + "|" + strategy.name() + "|" + (filters != null ? filters.toString() : "all");
        if (cacheService != null) {
            var cached = cacheService.getGeneric("retrieval", cacheKey, new com.fasterxml.jackson.core.type.TypeReference<List<ScoredChunk>>() {});
            if (cached.isPresent()) {
                if (observabilityService != null) {
                    observabilityService.recordRetrieval(java.time.Duration.ofNanos(System.nanoTime() - startRetrieval), cached.get().size(), strategy.name() + "_CACHED");
                }
                return cached.get();
            }
        }

        com.bank.aml.observability.RagObservabilityService.AutoCloseableSpan span =
            observabilityService != null ? observabilityService.startSpan("rag.retrieval", strategy.name()) : null;
        try {

        UUID docFilter = filters != null ? filters.documentId() : null;
        String typeFilter = filters != null ? filters.documentType() : null;
        String sectionFilter = filters != null ? filters.section() : null;

        // 1. Expand query if multi-query is active
        List<String> queryVariants = queryExpansionService.expandQuery(query);

        List<ChunkSimilarityProjection> aggregatedVectorCandidates = new ArrayList<>();
        List<ChunkSimilarityProjection> aggregatedKeywordCandidates = new ArrayList<>();

        int overfetchLimit = ragProperties.getTopK() * 3;
        double minSimilarity = ragProperties.getSimilarityThreshold();

        for (String q : queryVariants) {
            // A. Dense vector retrieval (unless KEYWORD_ONLY)
            if (strategy != RetrievalStrategy.KEYWORD_ONLY) {
                float[] embedding = embeddingGenerationService.generateEmbedding(q);
                List<ChunkSimilarityProjection> vectorResults = vectorRetrievalService.searchSimilarChunksWithFilters(
                    embedding,
                    minSimilarity,
                    docFilter,
                    typeFilter,
                    sectionFilter,
                    overfetchLimit
                );

                if (vectorResults.isEmpty()) {
                    // Adaptive fallback threshold
                    vectorResults = vectorRetrievalService.searchSimilarChunksWithFilters(
                        embedding,
                        0.10,
                        docFilter,
                        typeFilter,
                        sectionFilter,
                        overfetchLimit
                    );
                }
                aggregatedVectorCandidates.addAll(vectorResults);
            }

            // B. Keyword full-text retrieval (unless VECTOR_ONLY)
            if (strategy != RetrievalStrategy.VECTOR_ONLY) {
                List<ChunkSimilarityProjection> keywordResults = vectorRetrievalService.searchChunksByKeyword(
                    q,
                    docFilter,
                    typeFilter,
                    sectionFilter,
                    overfetchLimit
                );
                aggregatedKeywordCandidates.addAll(keywordResults);
            }
        }

        // 2. Pre-filter by authorized READY documents
        Set<UUID> docIds = new HashSet<>();
        aggregatedVectorCandidates.forEach(c -> docIds.add(c.getDocumentId()));
        aggregatedKeywordCandidates.forEach(c -> docIds.add(c.getDocumentId()));

        Map<UUID, Document> readyDocs = new HashMap<>();
        if (!docIds.isEmpty()) {
            for (Document d : documentRepository.findAllById(docIds)) {
                boolean statusOk = "READY".equalsIgnoreCase(d.getStatus()) || "ACTIVE".equalsIgnoreCase(d.getStatus());
                if (!statusOk) continue;

                if (filters != null) {
                    if (Boolean.TRUE.equals(filters.onlyEffective()) && !Boolean.TRUE.equals(d.getIsEffective())) {
                        continue;
                    }
                    if (filters.policyCode() != null && !filters.policyCode().isBlank() && !filters.policyCode().equalsIgnoreCase(d.getPolicyCode())) {
                        continue;
                    }
                    if (filters.jurisdiction() != null && !filters.jurisdiction().isBlank() && !filters.jurisdiction().equalsIgnoreCase(d.getJurisdiction())) {
                        continue;
                    }
                    if (filters.asOfDate() != null && d.getEffectiveDate() != null && d.getEffectiveDate().isAfter(filters.asOfDate())) {
                        continue;
                    }
                }
                readyDocs.put(d.getId(), d);
            }
        }

        List<ChunkSimilarityProjection> authorizedVectors = aggregatedVectorCandidates.stream()
            .filter(c -> readyDocs.containsKey(c.getDocumentId()))
            .toList();

        List<ChunkSimilarityProjection> authorizedKeywords = aggregatedKeywordCandidates.stream()
            .filter(c -> readyDocs.containsKey(c.getDocumentId()))
            .toList();

        if (authorizedVectors.isEmpty() && authorizedKeywords.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. Merge & Deduplicate via Reciprocal Rank Fusion (RRF)
        List<ScoredChunk> fusedChunks;
        if (strategy == RetrievalStrategy.VECTOR_ONLY) {
            fusedChunks = authorizedVectors.stream()
                .map(v -> {
                    Document doc = readyDocs.get(v.getDocumentId());
                    return ScoredChunk.fromProjection(v, doc != null ? doc.getTitle() : null, doc != null ? doc.getDocumentType() : null, "VECTOR");
                })
                .toList();
        } else if (strategy == RetrievalStrategy.KEYWORD_ONLY) {
            fusedChunks = authorizedKeywords.stream()
                .map(k -> {
                    Document doc = readyDocs.get(k.getDocumentId());
                    return ScoredChunk.fromProjection(k, doc != null ? doc.getTitle() : null, doc != null ? doc.getDocumentType() : null, "KEYWORD");
                })
                .toList();
        } else {
            fusedChunks = reciprocalRankFusionService.fuse(authorizedVectors, authorizedKeywords, readyDocs);
        }

        // 4. Contextual Reranking
        List<ScoredChunk> topCandidates;
        if (strategy == RetrievalStrategy.HYBRID_RERANK) {
            long startRerank = System.nanoTime();
            topCandidates = chunkRerankerService.rerank(query, fusedChunks, ragProperties.getTopK());
            if (observabilityService != null) {
                observabilityService.recordReranking(java.time.Duration.ofNanos(System.nanoTime() - startRerank), strategy.name());
            }
        } else {
            topCandidates = fusedChunks.stream().limit(ragProperties.getTopK()).toList();
        }

        // 5. Parent-Child Context Enrichment (if enabled)
        if (ragProperties.isParentChildEnabled()) {
            enrichWithParentContext(topCandidates);
        }

        if (observabilityService != null) {
            observabilityService.recordRetrieval(java.time.Duration.ofNanos(System.nanoTime() - startRetrieval), topCandidates.size(), strategy.name());
        }

        if (cacheService != null && !topCandidates.isEmpty()) {
            cacheService.put("retrieval", cacheKey, topCandidates, java.time.Duration.ofMinutes(15));
        }

        return topCandidates;
        } finally {
            if (span != null) {
                span.close();
            }
        }
    }

    private void enrichWithParentContext(List<ScoredChunk> chunks) {
        List<UUID> parentIds = chunks.stream()
            .map(ScoredChunk::getParentChunkId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        if (parentIds.isEmpty()) return;

        Map<UUID, String> parentTexts = vectorRetrievalService.findChunksByIds(parentIds).stream()
            .collect(Collectors.toMap(DocumentChunk::getId, DocumentChunk::getChunkText));

        for (ScoredChunk c : chunks) {
            if (c.getParentChunkId() != null && parentTexts.containsKey(c.getParentChunkId())) {
                c.setParentChunkText(parentTexts.get(c.getParentChunkId()));
            }
        }
    }

    private RetrievalStrategy resolveStrategy(RetrievalFilterDto filters) {
        if (filters != null && filters.retrievalStrategy() != null && !filters.retrievalStrategy().isBlank()) {
            try {
                return RetrievalStrategy.valueOf(filters.retrievalStrategy().toUpperCase().trim());
            } catch (IllegalArgumentException ignored) {}
        }
        try {
            return RetrievalStrategy.valueOf(ragProperties.getRetrievalStrategy().toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return RetrievalStrategy.HYBRID_RERANK;
        }
    }
}
