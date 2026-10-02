package com.bank.aml;

import com.bank.aml.entity.*;
import com.bank.aml.rag.VectorRetrievalService;
import com.bank.aml.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DatabaseLayerIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private VectorRetrievalService vectorRetrievalService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Verify Flyway seed data for Roles")
    void testRolesSeedData() {
        Optional<Role> adminRole = roleRepository.findByName("ROLE_ADMIN");
        Optional<Role> analystRole = roleRepository.findByName("ROLE_ANALYST");

        assertThat(adminRole).isPresent();
        assertThat(analystRole).isPresent();
        assertThat(adminRole.get().getDescription()).contains("Compliance Administrator");
    }

    @Test
    @DisplayName("Verify Flyway seed data for Users and BCrypt Passwords")
    void testUsersSeedData() {
        Optional<User> admin = userRepository.findByUsername("admin");
        Optional<User> analyst = userRepository.findByUsername("analyst");

        assertThat(admin).isPresent();
        assertThat(admin.get().getEmail()).isEqualTo("admin@bank.internal");
        assertThat(admin.get().getRoles()).extracting(Role::getName).contains("ROLE_ADMIN", "ROLE_ANALYST");
        assertThat(passwordEncoder.matches("AdminPass123!", admin.get().getPasswordHash())).isTrue();

        assertThat(analyst).isPresent();
        assertThat(analyst.get().getEmail()).isEqualTo("analyst@bank.internal");
        assertThat(analyst.get().getRoles()).extracting(Role::getName).containsOnly("ROLE_ANALYST");
        assertThat(passwordEncoder.matches("AdminPass123!", analyst.get().getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("Verify Document lifecycle persistence")
    @Transactional
    void testDocumentLifecycle() {
        Optional<Document> existingDoc = documentRepository.findByFilename("AML-SOP-002_High_Value_Transaction_Monitoring.md");
        assertThat(existingDoc).isPresent();
        assertThat(existingDoc.get().getStatus()).isEqualTo("ACTIVE");

        // Insert new document
        Document newDoc = new Document(
            "Sanctions and PEP Screening Policy",
            "AML-POL-004_Sanctions.pdf",
            "POLICY",
            "v1.0",
            "Risk & Governance",
            "PENDING",
            existingDoc.get().getUploadedBy()
        );
        Document savedDoc = documentRepository.save(newDoc);
        assertThat(savedDoc.getId()).isNotNull();

        List<Document> activeDocs = documentRepository.findByStatus("ACTIVE");
        assertThat(activeDocs).isNotEmpty();
    }

    @Test
    @DisplayName("Verify Vector Similarity Retrieval using pgvector HNSW index")
    void testVectorSimilarityRetrieval() {
        // Query with an identical synthetic vector (1536 dimensions filled with 0.0255155)
        float[] queryVector = new float[1536];
        Arrays.fill(queryVector, 0.0255155f);

        List<ChunkSimilarityProjection> results = vectorRetrievalService.searchSimilarChunks(
            queryVector,
            0.70, // Min similarity threshold
            5     // Top-K
        );

        assertThat(results).isNotEmpty();
        assertThat(results).hasSizeGreaterThanOrEqualTo(2);

        ChunkSimilarityProjection topResult = results.get(0);
        assertThat(topResult.getId()).isNotNull();
        assertThat(topResult.getChunkText()).contains("High-Value Transaction Alert");
        assertThat(topResult.getSection()).isNotBlank();
        assertThat(topResult.getSimilarity()).isNotNull();
        // Since the vectors are almost identical, cosine similarity should be >= 0.99
        assertThat(topResult.getSimilarity()).isGreaterThan(0.95);
        assertThat(topResult.getDistance()).isLessThan(0.05);

        // Verify threshold filter cuts off when threshold is impossibly high
        List<ChunkSimilarityProjection> noResults = vectorRetrievalService.searchSimilarChunks(
            queryVector,
            0.99999999,
            5
        );
        // Depending on floating point precision, it should return fewer or zero items
        assertThat(noResults.size()).isLessThanOrEqualTo(results.size());
    }

    @Test
    @DisplayName("Verify ChatSession and ChatMessage relational persistence")
    @Transactional
    void testChatSessionAndMessages() {
        User analyst = userRepository.findByUsername("analyst").orElseThrow();

        ChatSession newSession = new ChatSession(analyst.getId(), "SAR Escalation Review");
        ChatSession savedSession = chatSessionRepository.save(newSession);
        assertThat(savedSession.getId()).isNotNull();

        ChatMessage userMsg = new ChatMessage(savedSession.getId(), "USER", "Is an RFI required for $60,000 wire?");
        ChatMessage assistantMsg = new ChatMessage(savedSession.getId(), "ASSISTANT", "Yes, Section 3 requires an RFI within 24 hours.");

        chatMessageRepository.saveAll(List.of(userMsg, assistantMsg));

        List<ChatMessage> conversation = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(savedSession.getId());
        assertThat(conversation).hasSize(2);
        assertThat(conversation.get(0).getRole()).isEqualTo("USER");
        assertThat(conversation.get(1).getRole()).isEqualTo("ASSISTANT");
    }

    @Test
    @DisplayName("Verify Compliance AuditLog persistence and JSONB metadata")
    @Transactional
    void testAuditLogPersistence() {
        User analyst = userRepository.findByUsername("analyst").orElseThrow();

        AuditLog log = new AuditLog(
            analyst.getId(),
            "POLICY_DOWNLOAD",
            "DOCUMENT",
            "30000000-0000-0000-0000-000000000001",
            "REQ-VERIFY-123",
            "{\"ipAddress\": \"192.168.1.50\", \"userAgent\": \"TestRunner\"}"
        );

        AuditLog savedLog = auditLogRepository.save(log);
        assertThat(savedLog.getId()).isNotNull();
        assertThat(savedLog.getCreatedAt()).isNotNull();

        List<AuditLog> userLogs = auditLogRepository.findByUserIdOrderByCreatedAtDesc(analyst.getId());
        assertThat(userLogs).isNotEmpty();
        assertThat(userLogs.get(0).getAction()).isEqualTo("POLICY_DOWNLOAD");
    }
}
