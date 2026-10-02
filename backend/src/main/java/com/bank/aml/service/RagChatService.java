package com.bank.aml.service;

import com.bank.aml.dto.request.AskQuestionRequest;
import com.bank.aml.dto.request.CreateChatSessionRequest;
import com.bank.aml.dto.response.ChatMessageResponse;
import com.bank.aml.dto.response.ChatSessionResponse;
import com.bank.aml.dto.response.ChatStreamEvent;
import com.bank.aml.dto.response.CitationResponse;
import com.bank.aml.entity.ChatMessage;
import com.bank.aml.entity.ChatSession;
import com.bank.aml.entity.Document;
import com.bank.aml.rag.LlmGenerationService;
import com.bank.aml.repository.ChatMessageRepository;
import com.bank.aml.repository.ChatSessionRepository;
import com.bank.aml.repository.ChunkSimilarityProjection;
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
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.documentRepository = documentRepository;
        this.authorizedRetrievalService = authorizedRetrievalService;
        this.promptBuilder = promptBuilder;
        this.llmGenerationService = llmGenerationService;
        this.outputValidator = outputValidator;
        this.securityAuditService = securityAuditService;
    }

    @Transactional
    public ChatSessionResponse createSession(CreateChatSessionRequest request, SecurityUserPrincipal user) {
        String title = (request != null && request.title() != null && !request.title().isBlank())
            ? request.title().trim()
            : "AML Investigation " + UUID.randomUUID().toString().substring(0, 8);

        ChatSession session = new ChatSession(user.getId(), title);
        ChatSession saved = chatSessionRepository.save(session);
        return new ChatSessionResponse(saved.getId(), saved.getUserId(), saved.getTitle(), Collections.emptyList(), saved.getCreatedAt(), saved.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> getUserSessions(SecurityUserPrincipal user) {
        // Enforce user isolation: only fetch sessions belonging to the authenticated user
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
        // 1. Authorize session ownership (Prevent cross-user context leakage)
        ChatSession session = authorizedRetrievalService.validateSessionAccess(sessionId, user);

        String userQuery = request.message().trim();
        String requestId = UUID.randomUUID().toString();

        // 2. Input Validation & Guardrail Scan (Direct prompt injection, system prompt extraction, delimiter breakouts)
        RagSecurityGuardrails.ScanResult scanResult = guardrails.scanUserQuery(userQuery);

        if (!scanResult.safe()) {
            // Record security incident in audit log
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

            // Persist user question
            ChatMessage userMsg = new ChatMessage(sessionId, "USER", userQuery);
            chatMessageRepository.save(userMsg);

            // Refusal response
            String refusal = "I am an authorized AML Compliance Assistant. I can only provide guidance grounded in approved bank compliance policies. I cannot execute instructions that waive statutory AML requirements, override reporting thresholds, or disclose internal system parameters.";
            ChatMessage assistantMsg = new ChatMessage(sessionId, "ASSISTANT", refusal);
            ChatMessage savedAssistantMsg = chatMessageRepository.save(assistantMsg);

            return new ChatMessageResponse(savedAssistantMsg.getId(), "ASSISTANT", refusal, Collections.emptyList(), savedAssistantMsg.getCreatedAt());
        }

        // 3. Authorized Vector Retrieval (Only READY documents)
        List<ChunkSimilarityProjection> retrievedChunks = authorizedRetrievalService.retrieveAuthorizedContext(scanResult.sanitizedInput(), user);

        // 4. Scan retrieved chunks for indirect prompt injection attempts
        for (ChunkSimilarityProjection chunk : retrievedChunks) {
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

        // 5. Instruction Hierarchy & Secure Prompt Boundary Assembly
        InstructionHierarchyPromptBuilder.AssembledPrompt assembledPrompt = promptBuilder.buildSecurePrompt(
            scanResult.sanitizedInput(),
            retrievedChunks
        );

        // 6. LLM Generation
        String rawGeneratedAnswer = llmGenerationService.generateAnswer(assembledPrompt);

        // 7. Output Validation & Secret Leakage Inspection
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

        // 8. Persist User and Assistant Messages
        ChatMessage userMsg = new ChatMessage(sessionId, "USER", userQuery);
        chatMessageRepository.save(userMsg);

        ChatMessage assistantMsg = new ChatMessage(sessionId, "ASSISTANT", finalAnswer);
        ChatMessage savedAssistantMsg = chatMessageRepository.save(assistantMsg);

        // 9. Build Citations from retrieved chunks
        List<CitationResponse> citations = buildCitations(retrievedChunks);

        // 10. Audit Logging for RAG Execution
        securityAuditService.recordSecurityEvent(
            user.getId(),
            "RAG_QUERY_EXECUTED",
            "CHAT_SESSION",
            sessionId.toString(),
            requestId,
            Map.of("retrievedChunksCount", retrievedChunks.size(), "citationsCount", citations.size())
        );

        return new ChatMessageResponse(
            savedAssistantMsg.getId(),
            "ASSISTANT",
            finalAnswer,
            citations,
            savedAssistantMsg.getCreatedAt()
        );
    }

    private List<CitationResponse> buildCitations(List<ChunkSimilarityProjection> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return Collections.emptyList();
        }

        List<CitationResponse> citations = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (ChunkSimilarityProjection c : chunks) {
            String key = c.getDocumentId() + ":" + c.getSection();
            if (seen.add(key)) {
                String docTitle = documentRepository.findById(c.getDocumentId())
                    .map(Document::getTitle)
                    .orElse("AML Compliance Document");

                citations.add(new CitationResponse(
                    c.getDocumentId(),
                    docTitle,
                    c.getSection() != null ? c.getSection() : "General Standards",
                    c.getPageNumber() != null ? c.getPageNumber() : 1,
                    c.getSimilarity()
                ));
            }
        }

        return citations;
    }

    public Flux<ChatStreamEvent> streamQuestion(UUID sessionId, String rawQuery, SecurityUserPrincipal user, String correlationId) {
        // 1. Authorize session ownership
        ChatSession session = authorizedRetrievalService.validateSessionAccess(sessionId, user);
        String userQuery = rawQuery.trim();

        // 2. Input Validation & Guardrail Scan
        RagSecurityGuardrails.ScanResult scanResult = guardrails.scanUserQuery(userQuery);

        if (!scanResult.safe()) {
            securityAuditService.recordSecurityEvent(
                user.getId(),
                "PROMPT_INJECTION_BLOCKED",
                "CHAT_SESSION",
                sessionId.toString(),
                correlationId,
                Map.of(
                    "attackType", scanResult.attackType(),
                    "reason", scanResult.reason(),
                    "riskScore", scanResult.riskScore(),
                    "rawQuery", userQuery
                )
            );

            // Persist user and refusal messages
            ChatMessage userMsg = new ChatMessage(sessionId, "USER", userQuery);
            chatMessageRepository.save(userMsg);

            String refusal = "I am an authorized AML Compliance Assistant. I can only provide guidance grounded in approved bank compliance policies. I cannot execute instructions that waive statutory AML requirements, override reporting thresholds, or disclose internal system parameters.";
            ChatMessage assistantMsg = new ChatMessage(sessionId, "ASSISTANT", refusal);
            ChatMessage savedAssistantMsg = chatMessageRepository.save(assistantMsg);

            return Flux.just(
                ChatStreamEvent.start(correlationId, sessionId),
                ChatStreamEvent.token(correlationId, sessionId, refusal),
                ChatStreamEvent.complete(correlationId, sessionId, savedAssistantMsg.getId())
            );
        }

        // 3. Authorized Vector Retrieval
        List<ChunkSimilarityProjection> retrievedChunks = authorizedRetrievalService.retrieveAuthorizedContext(scanResult.sanitizedInput(), user);

        // 4. Scan retrieved chunks for indirect prompt injection
        for (ChunkSimilarityProjection chunk : retrievedChunks) {
            if (guardrails.containsIndirectInjection(chunk.getChunkText())) {
                securityAuditService.recordSecurityEvent(
                    user.getId(),
                    "MALICIOUS_DOCUMENT_INJECTION_FLAGGED",
                    "DOCUMENT_CHUNK",
                    chunk.getId().toString(),
                    correlationId,
                    Map.of(
                        "documentId", chunk.getDocumentId().toString(),
                        "section", chunk.getSection() != null ? chunk.getSection() : "unknown",
                        "detectedText", chunk.getChunkText().substring(0, Math.min(100, chunk.getChunkText().length()))
                    )
                );
            }
        }

        // 5. Build citations
        List<CitationResponse> citations = buildCitations(retrievedChunks);

        // 6. Persist User Message immediately so inquiry is recorded even if stream is interrupted
        ChatMessage userMsg = new ChatMessage(sessionId, "USER", userQuery);
        chatMessageRepository.save(userMsg);

        // Audit stream started
        securityAuditService.recordSecurityEvent(
            user.getId(),
            "RAG_STREAM_STARTED",
            "CHAT_SESSION",
            sessionId.toString(),
            correlationId,
            Map.of("retrievedChunksCount", retrievedChunks.size(), "citationsCount", citations.size())
        );

        // 7. Instruction Hierarchy Prompt Construction
        InstructionHierarchyPromptBuilder.AssembledPrompt assembledPrompt = promptBuilder.buildSecurePrompt(
            scanResult.sanitizedInput(),
            retrievedChunks
        );

        // 8. Stream execution with token accumulation, timeout, cancellation & completion handling
        StringBuilder accumulated = new StringBuilder();

        Flux<ChatStreamEvent> tokenEvents = llmGenerationService.streamAnswer(assembledPrompt)
            .timeout(Duration.ofSeconds(60))
            .map(token -> {
                accumulated.append(token);
                return ChatStreamEvent.token(correlationId, sessionId, token);
            })
            .doOnCancel(() -> {
                securityAuditService.recordSecurityEvent(
                    user.getId(),
                    "RAG_STREAM_CANCELLED",
                    "CHAT_SESSION",
                    sessionId.toString(),
                    correlationId,
                    Map.of("partialLength", accumulated.length())
                );
                if (accumulated.length() > 0) {
                    ChatMessage partialMsg = new ChatMessage(sessionId, "ASSISTANT", accumulated.toString() + " [Stream cancelled by user]");
                    chatMessageRepository.save(partialMsg);
                }
            })
            .onErrorResume(e -> {
                securityAuditService.recordSecurityEvent(
                    user.getId(),
                    "RAG_STREAM_FAILED",
                    "CHAT_SESSION",
                    sessionId.toString(),
                    correlationId,
                    Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown stream error")
                );
                return Flux.just(ChatStreamEvent.error(correlationId, sessionId, "Streaming response encountered an error: " + e.getMessage()));
            });

        // Combine: START event -> CITATIONS event -> TOKEN events -> COMPLETE event
        Flux<ChatStreamEvent> startEvents = Flux.just(
            ChatStreamEvent.start(correlationId, sessionId),
            ChatStreamEvent.citations(correlationId, sessionId, citations)
        );

        Mono<ChatStreamEvent> completionEvent = Mono.defer(() -> {
            String rawGeneratedAnswer = accumulated.toString();
            RagOutputValidator.OutputValidationResult outputValidation = outputValidator.validateOutput(rawGeneratedAnswer);
            String finalAnswer = outputValidation.validatedContent();

            if (!outputValidation.valid()) {
                securityAuditService.recordSecurityEvent(
                    user.getId(),
                    "OUTPUT_VALIDATION_VIOLATION",
                    "CHAT_SESSION",
                    sessionId.toString(),
                    correlationId,
                    Map.of(
                        "violationType", outputValidation.violationType(),
                        "violationDetail", outputValidation.violationDetail()
                    )
                );
            }

            ChatMessage assistantMsg = new ChatMessage(sessionId, "ASSISTANT", finalAnswer);
            ChatMessage savedAssistantMsg = chatMessageRepository.save(assistantMsg);

            securityAuditService.recordSecurityEvent(
                user.getId(),
                "RAG_STREAM_COMPLETED",
                "CHAT_SESSION",
                sessionId.toString(),
                correlationId,
                Map.of("finalLength", finalAnswer.length())
            );

            return Mono.just(ChatStreamEvent.complete(correlationId, sessionId, savedAssistantMsg.getId()));
        });

        return startEvents.concatWith(tokenEvents).concatWith(completionEvent);
    }
}
