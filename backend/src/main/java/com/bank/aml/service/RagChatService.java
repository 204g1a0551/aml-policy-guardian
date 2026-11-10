package com.bank.aml.service;

import com.bank.aml.config.RagProperties;
import com.bank.aml.dto.request.AskQuestionRequest;
import com.bank.aml.dto.request.CreateChatSessionRequest;
import com.bank.aml.dto.request.RetrievalFilterDto;
import com.bank.aml.dto.response.ChatMessageResponse;
import com.bank.aml.dto.response.ChatSessionResponse;
import com.bank.aml.dto.response.ChatStreamEvent;
import com.bank.aml.dto.response.CitationResponse;
import com.bank.aml.entity.ChatMessage;
import com.bank.aml.entity.ChatSession;
import com.bank.aml.entity.Document;
import com.bank.aml.rag.*;
import com.bank.aml.repository.ChatMessageRepository;
import com.bank.aml.repository.ChatSessionRepository;
import com.bank.aml.repository.DocumentRepository;
import com.bank.aml.security.SecurityUserPrincipal;
import com.bank.aml.security.rag.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

@Service
public class RagChatService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DocumentRepository documentRepository;
    private final AuthorizedRetrievalService authorizedRetrievalService;
    private final RagSecurityGuardrails guardrails = new RagSecurityGuardrails();
    private final InstructionHierarchyPromptBuilder promptBuilder;
    private final LlmGenerationService llmGenerationService;
    private final RagOutputValidator outputValidator;
    private final SecurityAuditService securityAuditService;
    private final CitationVerificationService citationVerificationService;
    private final EvidenceQualityEvaluator evidenceQualityEvaluator;
    private final RagProperties ragProperties;
    private final com.bank.aml.security.alert.SecurityAlertService securityAlertService;
    private final com.bank.aml.security.pii.PiiRedactionService piiRedactionService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.bank.aml.observability.RagObservabilityService observabilityService;

    @org.springframework.beans.factory.annotation.Autowired
    public RagChatService(
        ChatSessionRepository chatSessionRepository,
        ChatMessageRepository chatMessageRepository,
        DocumentRepository documentRepository,
        AuthorizedRetrievalService authorizedRetrievalService,
        InstructionHierarchyPromptBuilder promptBuilder,
        LlmGenerationService llmGenerationService,
        RagOutputValidator outputValidator,
        SecurityAuditService securityAuditService,
        CitationVerificationService citationVerificationService,
        EvidenceQualityEvaluator evidenceQualityEvaluator,
        RagProperties ragProperties,
        com.bank.aml.security.alert.SecurityAlertService securityAlertService,
        com.bank.aml.security.pii.PiiRedactionService piiRedactionService
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.documentRepository = documentRepository;
        this.authorizedRetrievalService = authorizedRetrievalService;
        this.promptBuilder = promptBuilder;
        this.llmGenerationService = llmGenerationService;
        this.outputValidator = outputValidator;
        this.securityAuditService = securityAuditService;
        this.citationVerificationService = citationVerificationService;
        this.evidenceQualityEvaluator = evidenceQualityEvaluator;
        this.ragProperties = ragProperties;
        this.securityAlertService = securityAlertService;
        this.piiRedactionService = piiRedactionService;
    }

    public RagChatService(
        ChatSessionRepository chatSessionRepository,
        ChatMessageRepository chatMessageRepository,
        DocumentRepository documentRepository,
        AuthorizedRetrievalService authorizedRetrievalService,
        InstructionHierarchyPromptBuilder promptBuilder,
        LlmGenerationService llmGenerationService,
        RagOutputValidator outputValidator,
        SecurityAuditService securityAuditService
    ) {
        this(
            chatSessionRepository,
            chatMessageRepository,
            documentRepository,
            authorizedRetrievalService,
            promptBuilder,
            llmGenerationService,
            outputValidator,
            securityAuditService,
            new CitationVerificationService(),
            new EvidenceQualityEvaluator(),
            new RagProperties(),
            null,
            new com.bank.aml.security.pii.PiiRedactionService()
        );
    }

    @Transactional
    public ChatSessionResponse createSession(CreateChatSessionRequest request, SecurityUserPrincipal user) {
        String title = (request != null && request.title() != null && !request.title().isBlank())
            ? request.title().trim()
            : "AML Investigation " + UUID.randomUUID().toString().substring(0, 8);

        String tenantId = user.getTenantId() != null ? user.getTenantId() : "default-bank";
        ChatSession session = new ChatSession(user.getId(), title, tenantId);
        ChatSession saved = chatSessionRepository.save(session);
        return new ChatSessionResponse(saved.getId(), saved.getUserId(), saved.getTitle(), Collections.emptyList(), saved.getCreatedAt(), saved.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> getUserSessions(SecurityUserPrincipal user) {
        List<ChatSession> sessions = chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(user.getId());
        return sessions.stream()
            .map(s -> new ChatSessionResponse(s.getId(), s.getUserId(), s.getTitle(), Collections.emptyList(), s.getCreatedAt(), s.getUpdatedAt()))
            .toList();
    }

    @Transactional(readOnly = true)
    public ChatSessionResponse getSessionWithMessages(UUID sessionId, SecurityUserPrincipal user) {
        ChatSession session = authorizedRetrievalService.validateSessionAccess(sessionId, user);
        List<ChatMessageResponse> messageResponses = getSessionMessages(sessionId, user);
        return new ChatSessionResponse(session.getId(), session.getUserId(), session.getTitle(), messageResponses, session.getCreatedAt(), session.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getSessionMessages(UUID sessionId, SecurityUserPrincipal user) {
        authorizedRetrievalService.validateSessionAccess(sessionId, user);
        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        return messages.stream()
            .map(m -> new ChatMessageResponse(m.getId(), m.getSessionId(), m.getRole(), m.getContent(), Collections.emptyList(), m.getCreatedAt()))
            .toList();
    }

    @Transactional
    public void deleteSession(UUID sessionId, SecurityUserPrincipal user) {
        authorizedRetrievalService.validateSessionAccess(sessionId, user);
        chatMessageRepository.deleteBySessionId(sessionId);
        chatSessionRepository.deleteById(sessionId);
    }

    @Transactional
    public ChatMessageResponse askQuestion(UUID sessionId, AskQuestionRequest request, SecurityUserPrincipal user) {
        long startTotal = System.nanoTime();
        // 1. Authorize session ownership
        ChatSession session = authorizedRetrievalService.validateSessionAccess(sessionId, user);

        String userQuery = request.message().trim();
        String correlationId = com.bank.aml.security.correlation.CorrelationIdContext.getCorrelationId();
        String requestId = correlationId;
        RetrievalFilterDto filters = request.filters();

        // 2. Input Validation & Guardrail Scan
        RagSecurityGuardrails.ScanResult scanResult = guardrails.scanUserQuery(userQuery);

        if (!scanResult.safe()) {
            if (securityAlertService != null) {
                securityAlertService.triggerAlert(
                    "PROMPT_INJECTION_DETECTED",
                    "HIGH",
                    user.getId(),
                    null,
                    Map.of(
                        "attackType", scanResult.attackType(),
                        "reason", scanResult.reason(),
                        "riskScore", scanResult.riskScore(),
                        "sessionId", sessionId.toString()
                    )
                );
            }

            securityAuditService.recordSecurityEvent(
                user.getId(),
                "PROMPT_INJECTION_BLOCKED",
                "CHAT_SESSION",
                sessionId.toString(),
                requestId,
                Map.of(
                    "attackType", scanResult.attackType(),
                    "reason", scanResult.reason(),
                    "riskScore", scanResult.riskScore(),
                    "rawQuery", userQuery
                )
            );

            ChatMessage userMsg = new ChatMessage(sessionId, "USER", userQuery);
            chatMessageRepository.save(userMsg);

            String refusal = "I am an authorized AML Compliance Assistant. I can only provide guidance grounded in approved bank compliance policies. I cannot execute instructions that waive statutory AML requirements, override reporting thresholds, or disclose internal system parameters.";
            ChatMessage assistantMsg = new ChatMessage(sessionId, "ASSISTANT", refusal);
            ChatMessage savedAssistantMsg = chatMessageRepository.save(assistantMsg);

            if (observabilityService != null) {
                observabilityService.recordTotalRequest(java.time.Duration.ofNanos(System.nanoTime() - startTotal), true, false);
            }

            return new ChatMessageResponse(
                savedAssistantMsg.getId(),
                sessionId,
                "ASSISTANT",
                refusal,
                Collections.emptyList(),
                savedAssistantMsg.getCreatedAt(),
                0.0,
                "INSUFFICIENT",
                true,
                "BLOCKED_BY_GUARDRAIL"
            );
        }

        // Apply PII Redaction before sending query to models or prompt builder
        String sanitizedQuery = scanResult.sanitizedInput();
        if (piiRedactionService != null) {
            com.bank.aml.security.pii.PiiRedactionService.RedactionResult pii = piiRedactionService.redact(sanitizedQuery);
            if (pii.piiDetected()) {
                securityAuditService.recordSecurityEvent(
                    user.getId(),
                    "PII_REDACTED_FROM_QUERY",
                    "CHAT_QUERY",
                    sessionId.toString(),
                    requestId,
                    Map.of("redactedCategories", pii.redactedCategories(), "count", pii.redactionsCount())
                );
                sanitizedQuery = pii.redactedText();
            }
        }

        // 3. Advanced Hybrid Retrieval (pgvector + tsvector + RRF + Reranker)
        List<ScoredChunk> retrievedChunks = authorizedRetrievalService.retrieveAuthorizedContext(
            sanitizedQuery, user, filters
        );

        // 4. Check for Explicit Insufficient-Evidence Policy
        if (retrievedChunks.isEmpty()) {
            ChatMessage userMsg = new ChatMessage(sessionId, "USER", userQuery);
            chatMessageRepository.save(userMsg);

            String refusal = "I cannot find sufficient guidance in the approved AML compliance policies to answer this inquiry. In accordance with bank compliance governance, please consult the Money Laundering Reporting Officer (MLRO) or refer to the primary policy library.";
            ChatMessage assistantMsg = new ChatMessage(sessionId, "ASSISTANT", refusal);
            ChatMessage savedAssistantMsg = chatMessageRepository.save(assistantMsg);

            if (observabilityService != null) {
                observabilityService.recordTotalRequest(java.time.Duration.ofNanos(System.nanoTime() - startTotal), true, false);
            }

            return new ChatMessageResponse(
                savedAssistantMsg.getId(),
                sessionId,
                "ASSISTANT",
                refusal,
                Collections.emptyList(),
                savedAssistantMsg.getCreatedAt(),
                0.0,
                "INSUFFICIENT",
                true,
                "INSUFFICIENT_EVIDENCE"
            );
        }

        // 5. Scan retrieved chunks for indirect prompt injection attempts
        for (ScoredChunk chunk : retrievedChunks) {
            if (guardrails.containsIndirectInjection(chunk.getChunkText())) {
                securityAuditService.recordSecurityEvent(
                    user.getId(),
                    "MALICIOUS_DOCUMENT_INJECTION_FLAGGED",
                    "DOCUMENT_CHUNK",
                    chunk.getId().toString(),
                    requestId,
                    Map.of(
                        "documentId", chunk.getDocumentId().toString(),
                        "section", chunk.getSection() != null ? chunk.getSection() : "unknown",
                        "detectedText", chunk.getChunkText().substring(0, Math.min(100, chunk.getChunkText().length()))
                    )
                );
            }
        }

        // 6. Instruction Hierarchy & Secure Prompt Assembly
        InstructionHierarchyPromptBuilder.AssembledPrompt assembledPrompt = promptBuilder.buildSecurePrompt(
            scanResult.sanitizedInput(),
            new ArrayList<>(retrievedChunks)
        );

        // 7. LLM Generation
        long startGen = System.nanoTime();
        String rawGeneratedAnswer = llmGenerationService.generateAnswer(assembledPrompt);
        if (observabilityService != null) {
            observabilityService.recordGeneration(java.time.Duration.ofNanos(System.nanoTime() - startGen), ragProperties.getLlmModel());
        }

        // 8. Output Validation
        RagOutputValidator.OutputValidationResult outputValidation = outputValidator.validateOutput(rawGeneratedAnswer);
        String finalAnswer = outputValidation.validatedContent();

        if (!outputValidation.valid()) {
            securityAuditService.recordSecurityEvent(
                user.getId(),
                "OUTPUT_VALIDATION_VIOLATION",
                "CHAT_SESSION",
                sessionId.toString(),
                requestId,
                Map.of(
                    "violationType", outputValidation.violationType(),
                    "violationDetail", outputValidation.violationDetail()
                )
            );
        }

        // 9. Claim-to-Citation Verification
        Map<UUID, Document> documentsMap = loadDocumentsMap(retrievedChunks);
        List<CitationResponse> verifiedCitations = citationVerificationService.verifyAndEnrichCitations(
            finalAnswer, retrievedChunks, documentsMap
        );

        // 10. Calibrated Evidence Quality Assessment
        boolean isRefusal = finalAnswer.contains("I cannot find sufficient guidance") || !outputValidation.valid();
        EvidenceQualityEvaluator.EvidenceAssessment assessment = evidenceQualityEvaluator.assessEvidence(
            userQuery, retrievedChunks, verifiedCitations, isRefusal
        );

        // 11. Persist User and Assistant Messages
        ChatMessage userMsg = new ChatMessage(sessionId, "USER", userQuery);
        chatMessageRepository.save(userMsg);

        ChatMessage assistantMsg = new ChatMessage(sessionId, "ASSISTANT", finalAnswer);
        ChatMessage savedAssistantMsg = chatMessageRepository.save(assistantMsg);

        // 12. Audit Logging
        String strategyUsed = filters != null && filters.retrievalStrategy() != null
            ? filters.retrievalStrategy()
            : ragProperties.getRetrievalStrategy();

        securityAuditService.recordSecurityEvent(
            user.getId(),
            "RAG_QUERY_EXECUTED",
            "CHAT_SESSION",
            sessionId.toString(),
            requestId,
            Map.of(
                "retrievedChunksCount", retrievedChunks.size(),
                "citationsCount", verifiedCitations.size(),
                "evidenceLevel", assessment.level(),
                "evidenceScore", assessment.score(),
                "strategy", strategyUsed
            )
        );

        if (observabilityService != null) {
            int promptTokens = Math.max(1, assembledPrompt.fullUserPrompt().length() / 4);
            int completionTokens = Math.max(1, finalAnswer.length() / 4);
            observabilityService.recordTokenUsage(promptTokens, completionTokens, ragProperties.getLlmModel());
            observabilityService.recordEvidenceQuality(assessment.level(), assessment.score());
            observabilityService.recordTotalRequest(java.time.Duration.ofNanos(System.nanoTime() - startTotal), isRefusal, false);
        }

        return new ChatMessageResponse(
            savedAssistantMsg.getId(),
            sessionId,
            "ASSISTANT",
            finalAnswer,
            verifiedCitations,
            savedAssistantMsg.getCreatedAt(),
            assessment.score(),
            assessment.level(),
            isRefusal,
            strategyUsed
        );
    }

    public Flux<ChatStreamEvent> streamQuestion(UUID sessionId, String rawQuery, SecurityUserPrincipal user, String correlationId) {
        return streamQuestion(sessionId, rawQuery, user, correlationId, null);
    }

    public Flux<ChatStreamEvent> streamQuestion(
        UUID sessionId,
        String rawQuery,
        SecurityUserPrincipal user,
        String correlationId,
        RetrievalFilterDto filters
    ) {
        final String streamCorrelationId = (correlationId != null && !correlationId.isBlank())
            ? correlationId
            : com.bank.aml.security.correlation.CorrelationIdContext.getCorrelationId();
        final long startStream = System.nanoTime();
        final java.util.concurrent.atomic.AtomicBoolean firstTokenSeen = new java.util.concurrent.atomic.AtomicBoolean(false);

        ChatSession session = authorizedRetrievalService.validateSessionAccess(sessionId, user);
        String userQuery = rawQuery.trim();

        // Guardrail Scan
        RagSecurityGuardrails.ScanResult scanResult = guardrails.scanUserQuery(userQuery);

        if (!scanResult.safe()) {
            if (securityAlertService != null) {
                securityAlertService.triggerAlert(
                    "PROMPT_INJECTION_DETECTED",
                    "HIGH",
                    user.getId(),
                    null,
                    Map.of(
                        "attackType", scanResult.attackType(),
                        "reason", scanResult.reason(),
                        "riskScore", scanResult.riskScore(),
                        "sessionId", sessionId.toString()
                    )
                );
            }

            securityAuditService.recordSecurityEvent(
                user.getId(),
                "PROMPT_INJECTION_BLOCKED",
                "CHAT_SESSION",
                sessionId.toString(),
                streamCorrelationId,
                Map.of(
                    "attackType", scanResult.attackType(),
                    "reason", scanResult.reason(),
                    "riskScore", scanResult.riskScore(),
                    "rawQuery", userQuery
                )
            );

            ChatMessage userMsg = new ChatMessage(sessionId, "USER", userQuery);
            chatMessageRepository.save(userMsg);

            String refusal = "I am an authorized AML Compliance Assistant. I can only provide guidance grounded in approved bank compliance policies. I cannot execute instructions that waive statutory AML requirements, override reporting thresholds, or disclose internal system parameters.";
            ChatMessage assistantMsg = new ChatMessage(sessionId, "ASSISTANT", refusal);
            ChatMessage savedAssistantMsg = chatMessageRepository.save(assistantMsg);

            if (observabilityService != null) {
                observabilityService.recordTotalRequest(java.time.Duration.ofNanos(System.nanoTime() - startStream), true, false);
            }

            return Flux.just(
                ChatStreamEvent.start(streamCorrelationId, sessionId),
                ChatStreamEvent.token(streamCorrelationId, sessionId, refusal),
                ChatStreamEvent.complete(streamCorrelationId, sessionId, savedAssistantMsg.getId())
            );
        }

        // Apply PII Redaction
        String sanitizedQuery = scanResult.sanitizedInput();
        if (piiRedactionService != null) {
            com.bank.aml.security.pii.PiiRedactionService.RedactionResult pii = piiRedactionService.redact(sanitizedQuery);
            if (pii.piiDetected()) {
                securityAuditService.recordSecurityEvent(
                    user.getId(),
                    "PII_REDACTED_FROM_QUERY",
                    "CHAT_QUERY",
                    sessionId.toString(),
                    streamCorrelationId,
                    Map.of("redactedCategories", pii.redactedCategories(), "count", pii.redactionsCount())
                );
                sanitizedQuery = pii.redactedText();
            }
        }

        // Advanced Hybrid Retrieval
        List<ScoredChunk> retrievedChunks = authorizedRetrievalService.retrieveAuthorizedContext(
            sanitizedQuery, user, filters
        );

        if (retrievedChunks.isEmpty()) {
            ChatMessage userMsg = new ChatMessage(sessionId, "USER", userQuery);
            chatMessageRepository.save(userMsg);

            String refusal = "I cannot find sufficient guidance in the approved AML compliance policies to answer this inquiry. In accordance with bank compliance governance, please consult the Money Laundering Reporting Officer (MLRO) or refer to the primary policy library.";
            ChatMessage assistantMsg = new ChatMessage(sessionId, "ASSISTANT", refusal);
            ChatMessage savedAssistantMsg = chatMessageRepository.save(assistantMsg);

            if (observabilityService != null) {
                observabilityService.recordTotalRequest(java.time.Duration.ofNanos(System.nanoTime() - startStream), true, false);
            }

            return Flux.just(
                ChatStreamEvent.start(streamCorrelationId, sessionId),
                ChatStreamEvent.token(streamCorrelationId, sessionId, refusal),
                ChatStreamEvent.complete(streamCorrelationId, sessionId, savedAssistantMsg.getId(), 0.0, "INSUFFICIENT")
            );
        }

        // Build verified citations
        Map<UUID, Document> documentsMap = loadDocumentsMap(retrievedChunks);
        List<CitationResponse> initialCitations = citationVerificationService.verifyAndEnrichCitations(
            userQuery, retrievedChunks, documentsMap
        );

        // Pre-assess evidence
        EvidenceQualityEvaluator.EvidenceAssessment initialAssessment = evidenceQualityEvaluator.assessEvidence(
            userQuery, retrievedChunks, initialCitations, false
        );

        String strategyUsed = filters != null && filters.retrievalStrategy() != null
            ? filters.retrievalStrategy()
            : ragProperties.getRetrievalStrategy();

        // Persist User Message
        ChatMessage userMsg = new ChatMessage(sessionId, "USER", userQuery);
        chatMessageRepository.save(userMsg);

        securityAuditService.recordSecurityEvent(
            user.getId(),
            "RAG_STREAM_STARTED",
            "CHAT_SESSION",
            sessionId.toString(),
            streamCorrelationId,
            Map.of("retrievedChunksCount", retrievedChunks.size(), "strategy", strategyUsed)
        );

        InstructionHierarchyPromptBuilder.AssembledPrompt assembledPrompt = promptBuilder.buildSecurePrompt(
            scanResult.sanitizedInput(),
            new ArrayList<>(retrievedChunks)
        );

        StringBuilder accumulated = new StringBuilder();

        Flux<ChatStreamEvent> tokenEvents = llmGenerationService.streamAnswer(assembledPrompt)
            .timeout(Duration.ofSeconds(60))
            .map(token -> {
                if (firstTokenSeen.compareAndSet(false, true)) {
                    if (observabilityService != null) {
                        observabilityService.recordTimeToFirstToken(java.time.Duration.ofNanos(System.nanoTime() - startStream));
                    }
                }
                accumulated.append(token);
                return ChatStreamEvent.token(streamCorrelationId, sessionId, token);
            })
            .doOnCancel(() -> {
                securityAuditService.recordSecurityEvent(
                    user.getId(),
                    "RAG_STREAM_CANCELLED",
                    "CHAT_SESSION",
                    sessionId.toString(),
                    streamCorrelationId,
                    Map.of("partialLength", accumulated.length())
                );
                if (accumulated.length() > 0) {
                    ChatMessage partialMsg = new ChatMessage(sessionId, "ASSISTANT", accumulated.toString() + " [Stream cancelled by user]");
                    chatMessageRepository.save(partialMsg);
                }
            })
            .onErrorResume(e -> {
                if (observabilityService != null) {
                    observabilityService.recordModelFailure(e.getMessage());
                    observabilityService.recordTotalRequest(java.time.Duration.ofNanos(System.nanoTime() - startStream), false, true);
                }
                securityAuditService.recordSecurityEvent(
                    user.getId(),
                    "RAG_STREAM_FAILED",
                    "CHAT_SESSION",
                    sessionId.toString(),
                    streamCorrelationId,
                    Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown stream error")
                );
                return Flux.just(ChatStreamEvent.error(streamCorrelationId, sessionId, "Streaming response encountered an error: " + e.getMessage()));
            });

        Flux<ChatStreamEvent> startEvents = Flux.just(
            ChatStreamEvent.start(streamCorrelationId, sessionId),
            ChatStreamEvent.citations(streamCorrelationId, sessionId, initialCitations, initialAssessment.score(), initialAssessment.level(), strategyUsed)
        );

        Mono<ChatStreamEvent> completionEvent = Mono.defer(() -> {
            String rawGeneratedAnswer = accumulated.toString();
            RagOutputValidator.OutputValidationResult outputValidation = outputValidator.validateOutput(rawGeneratedAnswer);
            String finalAnswer = outputValidation.validatedContent();

            ChatMessage assistantMsg = new ChatMessage(sessionId, "ASSISTANT", finalAnswer);
            ChatMessage savedAssistantMsg = chatMessageRepository.save(assistantMsg);

            List<CitationResponse> finalCitations = citationVerificationService.verifyAndEnrichCitations(
                finalAnswer, retrievedChunks, documentsMap
            );
            EvidenceQualityEvaluator.EvidenceAssessment finalAssessment = evidenceQualityEvaluator.assessEvidence(
                userQuery, retrievedChunks, finalCitations, finalAnswer.contains("I cannot find sufficient guidance")
            );

            securityAuditService.recordSecurityEvent(
                user.getId(),
                "RAG_STREAM_COMPLETED",
                "CHAT_SESSION",
                sessionId.toString(),
                streamCorrelationId,
                Map.of("finalLength", finalAnswer.length(), "evidenceScore", finalAssessment.score())
            );

            if (observabilityService != null) {
                observabilityService.recordGeneration(java.time.Duration.ofNanos(System.nanoTime() - startStream), ragProperties.getLlmModel());
                int promptTokens = Math.max(1, assembledPrompt.fullUserPrompt().length() / 4);
                int completionTokens = Math.max(1, finalAnswer.length() / 4);
                observabilityService.recordTokenUsage(promptTokens, completionTokens, ragProperties.getLlmModel());
                observabilityService.recordEvidenceQuality(finalAssessment.level(), finalAssessment.score());
                observabilityService.recordTotalRequest(java.time.Duration.ofNanos(System.nanoTime() - startStream), finalAnswer.contains("I cannot find sufficient guidance"), false);
            }

            return Mono.just(ChatStreamEvent.complete(
                streamCorrelationId, sessionId, savedAssistantMsg.getId(), finalAssessment.score(), finalAssessment.level()
            ));
        });

        return startEvents.concatWith(tokenEvents).concatWith(completionEvent);
    }

    private Map<UUID, Document> loadDocumentsMap(List<ScoredChunk> chunks) {
        Set<UUID> docIds = new HashSet<>();
        for (ScoredChunk c : chunks) {
            docIds.add(c.getDocumentId());
        }
        Map<UUID, Document> map = new HashMap<>();
        if (!docIds.isEmpty()) {
            for (Document d : documentRepository.findAllById(docIds)) {
                map.put(d.getId(), d);
            }
        }
        return map;
    }
}
