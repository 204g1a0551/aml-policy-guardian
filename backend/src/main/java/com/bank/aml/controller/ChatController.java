package com.bank.aml.controller;

import com.bank.aml.dto.request.AskQuestionRequest;
import com.bank.aml.dto.request.CreateChatSessionRequest;
import com.bank.aml.dto.response.ChatMessageResponse;
import com.bank.aml.dto.response.ChatSessionResponse;
import com.bank.aml.security.SecurityUserPrincipal;
import com.bank.aml.service.RagChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.bank.aml.dto.request.StreamQuestionRequest;
import com.bank.aml.dto.response.ChatStreamEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
@PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
public class ChatController {

    private final RagChatService ragChatService;
    private final com.bank.aml.repository.UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ChatController(
        RagChatService ragChatService,
        com.bank.aml.repository.UserRepository userRepository,
        ObjectMapper objectMapper
    ) {
        this.ragChatService = ragChatService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @PostMapping("/sessions")
    public ResponseEntity<ChatSessionResponse> createSession(
        @RequestBody(required = false) @Valid CreateChatSessionRequest request,
        @AuthenticationPrincipal SecurityUserPrincipal user,
        java.security.Principal principal
    ) {
        SecurityUserPrincipal resolved = resolvePrincipal(user, principal);
        ChatSessionResponse session = ragChatService.createSession(request, resolved);
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSessionResponse>> getUserSessions(
        @AuthenticationPrincipal SecurityUserPrincipal user,
        java.security.Principal principal
    ) {
        SecurityUserPrincipal resolved = resolvePrincipal(user, principal);
        List<ChatSessionResponse> sessions = ragChatService.getUserSessions(resolved);
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<ChatSessionResponse> getSessionWithMessages(
        @PathVariable UUID id,
        @AuthenticationPrincipal SecurityUserPrincipal user,
        java.security.Principal principal
    ) {
        SecurityUserPrincipal resolved = resolvePrincipal(user, principal);
        ChatSessionResponse session = ragChatService.getSessionWithMessages(id, resolved);
        return ResponseEntity.ok(session);
    }

    @PostMapping("/sessions/{id}/messages")
    public ResponseEntity<ChatMessageResponse> askQuestion(
        @PathVariable UUID id,
        @RequestBody @Valid AskQuestionRequest request,
        @AuthenticationPrincipal SecurityUserPrincipal user,
        java.security.Principal principal
    ) {
        SecurityUserPrincipal resolved = resolvePrincipal(user, principal);
        ChatMessageResponse response = ragChatService.askQuestion(id, request, resolved);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(
        @RequestParam(value = "sessionId", required = false) UUID paramSessionId,
        @RequestHeader(value = "X-Correlation-ID", required = false) String headerCorrelationId,
        @RequestBody(required = false) StreamQuestionRequest request,
        @AuthenticationPrincipal SecurityUserPrincipal user,
        java.security.Principal principal
    ) {
        SecurityUserPrincipal resolved = resolvePrincipal(user, principal);
        UUID targetSessionId = (request != null && request.sessionId() != null) ? request.sessionId() : paramSessionId;
        if (targetSessionId == null) {
            ChatSessionResponse session = ragChatService.createSession(null, resolved);
            targetSessionId = session.id();
        }

        String message = request != null ? request.message() : null;
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Inquiry message cannot be blank");
        }

        String correlationId = (headerCorrelationId != null && !headerCorrelationId.isBlank())
            ? headerCorrelationId
            : UUID.randomUUID().toString();

        final UUID finalSessionId = targetSessionId;
        return ragChatService.streamQuestion(finalSessionId, message, resolved, correlationId)
            .map(event -> {
                try {
                    String json = objectMapper.writeValueAsString(event);
                    return ServerSentEvent.<String>builder()
                        .id(correlationId)
                        .event(event.type())
                        .data(json)
                        .build();
                } catch (Exception e) {
                    return ServerSentEvent.<String>builder()
                        .id(correlationId)
                        .event("ERROR")
                        .data("{\"type\":\"ERROR\",\"error\":\"Failed to serialize stream event\"}")
                        .build();
                }
            });
    }

    private SecurityUserPrincipal resolvePrincipal(SecurityUserPrincipal user, java.security.Principal principal) {
        if (user != null) {
            return user;
        }
        if (principal != null) {
            return userRepository.findByUsername(principal.getName())
                .map(SecurityUserPrincipal::create)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("User not found: " + principal.getName()));
        }
        throw new org.springframework.security.access.AccessDeniedException("Unauthenticated");
    }
}
