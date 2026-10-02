package com.bank.aml.unit;

import com.bank.aml.config.RagProperties;
import com.bank.aml.embedding.EmbeddingGenerationService;
import com.bank.aml.entity.ChatSession;
import com.bank.aml.entity.Role;
import com.bank.aml.entity.User;
import com.bank.aml.exception.ResourceNotFoundException;
import com.bank.aml.rag.VectorRetrievalService;
import com.bank.aml.repository.ChatSessionRepository;
import com.bank.aml.repository.DocumentRepository;
import com.bank.aml.security.JwtTokenProvider;
import com.bank.aml.security.SecurityUserPrincipal;
import com.bank.aml.security.rag.AuthorizedRetrievalService;
import com.bank.aml.security.rag.SecurityAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SecurityLogicUnitTest {

    private JwtTokenProvider jwtTokenProvider;
    private final String testSecret = "0123456789012345678901234567890123456789012345678901234567890123"; // 64 bytes
    private final long testExpirationMs = 3600000;

    private ChatSessionRepository chatSessionRepository;
    private SecurityAuditService securityAuditService;
    private AuthorizedRetrievalService authorizedRetrievalService;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(testSecret, testExpirationMs);

        chatSessionRepository = mock(ChatSessionRepository.class);
        securityAuditService = mock(SecurityAuditService.class);
        VectorRetrievalService vectorRetrievalService = mock(VectorRetrievalService.class);
        EmbeddingGenerationService embeddingGenerationService = mock(EmbeddingGenerationService.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        RagProperties ragProperties = new RagProperties();

        authorizedRetrievalService = new AuthorizedRetrievalService(
            vectorRetrievalService,
            embeddingGenerationService,
            documentRepository,
            chatSessionRepository,
            securityAuditService,
            ragProperties
        );
    }

    // ==========================================
    // 1. JWT TOKEN PROVIDER TESTS
    // ==========================================

    @Test
    @DisplayName("JWT: Generates and validates token for authenticated user")
    void testJwtTokenGenerationAndValidation() {
        UUID userId = UUID.randomUUID();
        User user = new User("investigator", "$2a$12$dummy", "FIU Investigator", "inv@bank.internal");
        user.setId(userId);
        user.setEnabled(true);
        Role role = new Role("ROLE_ANALYST", "Analyst");
        user.setRoles(Set.of(role));

        SecurityUserPrincipal principal = SecurityUserPrincipal.create(user);

        String token = jwtTokenProvider.generateToken(principal);

        assertThat(token).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getUsernameFromToken(token)).isEqualTo("investigator");
        assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo(userId);
    }

    @Test
    @DisplayName("JWT: Rejects tampered, malformed, or altered tokens")
    void testJwtTokenRejectsTamperedSignature() {
        UUID userId = UUID.randomUUID();
        User user = new User("analyst", "$2a$12$dummy", "Analyst", "a@bank.internal");
        user.setId(userId);
        user.setEnabled(true);
        user.setRoles(Set.of(new Role("ROLE_ANALYST", "Analyst")));

        String validToken = jwtTokenProvider.generateToken(SecurityUserPrincipal.create(user));

        String tamperedToken = validToken.substring(0, validToken.length() - 5) + "abcde";

        assertThat(jwtTokenProvider.validateToken(tamperedToken)).isFalse();
        assertThat(jwtTokenProvider.validateToken("malformed.jwt.token")).isFalse();
        assertThat(jwtTokenProvider.validateToken("")).isFalse();
        assertThat(jwtTokenProvider.validateToken(null)).isFalse();
    }

    @Test
    @DisplayName("JWT: Rejects expired token")
    void testJwtTokenRejectsExpiredToken() {
        JwtTokenProvider expiredProvider = new JwtTokenProvider(testSecret, -1000);

        User user = new User("expiredUser", "$2a$12$dummy", "Expired", "e@bank.internal");
        user.setId(UUID.randomUUID());
        user.setEnabled(true);
        user.setRoles(Set.of(new Role("ROLE_ANALYST", "Analyst")));

        String expiredToken = expiredProvider.generateToken(SecurityUserPrincipal.create(user));

        assertThat(jwtTokenProvider.validateToken(expiredToken)).isFalse();
    }

    // ==========================================
    // 2. SECURITY USER PRINCIPAL TESTS
    // ==========================================

    @Test
    @DisplayName("SecurityUserPrincipal: Correctly exposes authorities, ID, and active status")
    void testSecurityUserPrincipalMapping() {
        UUID userId = UUID.randomUUID();
        User user = new User("adminUser", "$2a$12$dummy", "System Admin", "admin@bank.internal");
        user.setId(userId);
        user.setEnabled(true);
        user.setRoles(Set.of(new Role("ROLE_ADMIN", "Admin"), new Role("ROLE_ANALYST", "Analyst")));

        SecurityUserPrincipal principal = SecurityUserPrincipal.create(user);

        assertThat(principal.getId()).isEqualTo(userId);
        assertThat(principal.getUsername()).isEqualTo("adminUser");
        assertThat(principal.isEnabled()).isTrue();
        assertThat(principal.isAccountNonExpired()).isTrue();
        assertThat(principal.isAccountNonLocked()).isTrue();
        assertThat(principal.isCredentialsNonExpired()).isTrue();
        assertThat(principal.getAuthorities()).extracting("authority").containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_ANALYST");
    }

    // ==========================================
    // 3. AUTHORIZED RETRIEVAL & IDOR TESTS
    // ==========================================

    @Test
    @DisplayName("AuthorizedRetrieval: Allows session owner to access their own session")
    void testValidateSessionAccessOwnerAllowed() {
        UUID ownerId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        User user = new User("owner", "$2a$12$dummy", "Owner", "owner@bank.internal");
        user.setId(ownerId);
        user.setEnabled(true);
        user.setRoles(Set.of(new Role("ROLE_ANALYST", "Analyst")));
        SecurityUserPrincipal principal = SecurityUserPrincipal.create(user);

        ChatSession session = new ChatSession(ownerId, "Investigation Session");
        session.setId(sessionId);

        when(chatSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        ChatSession result = authorizedRetrievalService.validateSessionAccess(sessionId, principal);
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(sessionId);
    }

    @Test
    @DisplayName("AuthorizedRetrieval: Allows ADMIN to access any user session for oversight")
    void testValidateSessionAccessAdminAllowed() {
        UUID ownerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        User adminUser = new User("admin", "$2a$12$dummy", "Admin", "admin@bank.internal");
        adminUser.setId(adminId);
        adminUser.setEnabled(true);
        adminUser.setRoles(Set.of(new Role("ROLE_ADMIN", "Admin")));
        SecurityUserPrincipal adminPrincipal = SecurityUserPrincipal.create(adminUser);

        ChatSession session = new ChatSession(ownerId, "Analyst Session");
        session.setId(sessionId);

        when(chatSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        ChatSession result = authorizedRetrievalService.validateSessionAccess(sessionId, adminPrincipal);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("AuthorizedRetrieval: Rejects different analyst accessing another user's session (IDOR protection)")
    void testValidateSessionAccessDifferentAnalystDenied() {
        UUID ownerId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        User attacker = new User("attackerAnalyst", "$2a$12$dummy", "Attacker", "att@bank.internal");
        attacker.setId(attackerId);
        attacker.setEnabled(true);
        attacker.setRoles(Set.of(new Role("ROLE_ANALYST", "Analyst")));
        SecurityUserPrincipal attackerPrincipal = SecurityUserPrincipal.create(attacker);

        ChatSession session = new ChatSession(ownerId, "Private Investigation");
        session.setId(sessionId);

        when(chatSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> authorizedRetrievalService.validateSessionAccess(sessionId, attackerPrincipal))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("Access denied");

        verify(securityAuditService, times(1)).recordSecurityEvent(
            eq(attackerId),
            eq("CROSS_USER_ACCESS_DENIED"),
            eq("CHAT_SESSION"),
            eq(sessionId.toString()),
            any(),
            any()
        );
    }

    @Test
    @DisplayName("AuthorizedRetrieval: Throws ResourceNotFoundException for non-existent session ID")
    void testValidateSessionAccessNotFoundThrowsException() {
        UUID missingId = UUID.randomUUID();
        User user = new User("user", "$2a$12$dummy", "User", "u@bank.internal");
        user.setId(UUID.randomUUID());
        user.setEnabled(true);
        user.setRoles(Set.of(new Role("ROLE_ANALYST", "Analyst")));

        when(chatSessionRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authorizedRetrievalService.validateSessionAccess(missingId, SecurityUserPrincipal.create(user)))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Chat session not found with ID");
    }
}
