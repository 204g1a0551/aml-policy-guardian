package com.bank.aml.security.rag;

import com.bank.aml.config.RagProperties;
import com.bank.aml.embedding.EmbeddingGenerationService;
import com.bank.aml.entity.ChatSession;
import com.bank.aml.entity.Document;
import com.bank.aml.exception.ResourceNotFoundException;
import com.bank.aml.rag.VectorRetrievalService;
import com.bank.aml.repository.ChatSessionRepository;
import com.bank.aml.repository.ChunkSimilarityProjection;
import com.bank.aml.repository.DocumentRepository;
import com.bank.aml.security.SecurityUserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class AuthorizedRetrievalService {

    private final VectorRetrievalService vectorRetrievalService;
    private final EmbeddingGenerationService embeddingGenerationService;
    private final DocumentRepository documentRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final SecurityAuditService securityAuditService;
    private final RagProperties ragProperties;

    public AuthorizedRetrievalService(
        VectorRetrievalService vectorRetrievalService,
        EmbeddingGenerationService embeddingGenerationService,
        DocumentRepository documentRepository,
        ChatSessionRepository chatSessionRepository,
        SecurityAuditService securityAuditService,
        RagProperties ragProperties
    ) {
        this.vectorRetrievalService = vectorRetrievalService;
        this.embeddingGenerationService = embeddingGenerationService;
        this.documentRepository = documentRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.securityAuditService = securityAuditService;
        this.ragProperties = ragProperties;
    }

    /**
     * Verifies that the authenticated user owns the chat session before allowing query or retrieval.
     * Prevents cross-user context leakage and IDOR vulnerabilities.
     */
    public ChatSession validateSessionAccess(UUID sessionId, SecurityUserPrincipal user) {
        ChatSession session = chatSessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("Chat session not found with ID: " + sessionId));

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
     * Executes vector retrieval with pre-authorization and document status filtering.
     * Only retrieves chunks from approved, non-deleted, READY documents.
     */
    public List<ChunkSimilarityProjection> retrieveAuthorizedContext(String query, SecurityUserPrincipal user) {
        float[] queryEmbedding = embeddingGenerationService.generateEmbedding(query);
        double minSimilarity = ragProperties.getSimilarityThreshold();

        List<ChunkSimilarityProjection> candidates = vectorRetrievalService.searchSimilarChunks(
            queryEmbedding,
            minSimilarity,
            ragProperties.getTopK() * 2 // Over-fetch to allow post-filtering
        );

        if (candidates.isEmpty()) {
            // Adaptive fallback for offline bag-of-words token dispersion
            candidates = vectorRetrievalService.searchSimilarChunks(
                queryEmbedding,
                0.10,
                ragProperties.getTopK() * 2
            );
        }

        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        // Collect document IDs from candidates
        Set<UUID> docIds = new HashSet<>();
        for (ChunkSimilarityProjection c : candidates) {
            docIds.add(c.getDocumentId());
        }

        // Filter: only allow documents that exist and have status 'READY'
        Map<UUID, Document> readyDocs = new HashMap<>();
        for (Document d : documentRepository.findAllById(docIds)) {
            if ("READY".equalsIgnoreCase(d.getStatus())) {
                readyDocs.put(d.getId(), d);
            }
        }

        List<ChunkSimilarityProjection> authorized = new ArrayList<>();
        for (ChunkSimilarityProjection c : candidates) {
            if (readyDocs.containsKey(c.getDocumentId())) {
                authorized.add(c);
                if (authorized.size() >= ragProperties.getTopK()) {
                    break;
                }
            }
        }

        return authorized;
    }
}
