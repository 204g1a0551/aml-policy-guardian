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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
@PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
public class ChatController {

    private final RagChatService ragChatService;
    private final com.bank.aml.repository.UserRepository userRepository;

    public ChatController(RagChatService ragChatService, com.bank.aml.repository.UserRepository userRepository) {
        this.ragChatService = ragChatService;
        this.userRepository = userRepository;
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
