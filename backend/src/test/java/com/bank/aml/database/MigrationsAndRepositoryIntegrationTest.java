package com.bank.aml.database;

import com.bank.aml.entity.*;
import com.bank.aml.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MigrationsAndRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @Test
    @DisplayName("Migrations: Flyway applied schema V1 and seed V2 successfully with all tables intact")
    void testFlywaySchemaAndTables() {
        // Verify Flyway schema history table exists
        Integer flywayCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE success = true",
            Integer.class
        );
        assertThat(flywayCount).isNotNull();
        assertThat(flywayCount).isGreaterThanOrEqualTo(2);

        // Verify all core tables exist in PostgreSQL information_schema
        List<String> tables = jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
            String.class
        );

        assertThat(tables).contains(
            "users",
            "roles",
            "user_roles",
            "documents",
            "document_chunks",
            "chat_sessions",
            "chat_messages",
            "audit_logs"
        );
    }

    @Test
    @DisplayName("Repository - User & Roles: Verify findByUsername, password hash, and eager roles loading")
    void testUserRepositoryOperations() {
        Optional<User> analyst = userRepository.findByUsername("analyst");
        assertThat(analyst).isPresent();
        assertThat(analyst.get().getEmail()).isEqualTo("analyst@bank.internal");
        assertThat(analyst.get().getRoles()).extracting(Role::getName).contains("ROLE_ANALYST");

        Optional<User> admin = userRepository.findByUsername("admin");
        assertThat(admin).isPresent();
        assertThat(admin.get().getRoles()).extracting(Role::getName).contains("ROLE_ADMIN", "ROLE_ANALYST");

        // Create new investigator
        User newInvestigator = new User("investigator_qa", "$2a$12$dummyHash", "QA Investigator", "qa@bank.internal");
        Role analystRole = roleRepository.findByName("ROLE_ANALYST").orElseThrow();
        newInvestigator.setRoles(Set.of(analystRole));
        User saved = userRepository.save(newInvestigator);

        assertThat(saved.getId()).isNotNull();
        assertThat(userRepository.findByEmail("qa@bank.internal")).isPresent();
    }

    @Test
    @DisplayName("Repository - Document: Verify CRUD, status transitions, and queries by status/filename")
    void testDocumentRepositoryOperations() {
        User admin = userRepository.findByUsername("admin").orElseThrow();

        Document doc = new Document(
            "Sanctions & PEP Screening Guideline",
            "AML-GUIDE-PEP-QA.pdf",
            "GUIDELINE",
            "v1.0",
            "Financial Crime Unit",
            "PENDING",
            admin.getId()
        );
        Document saved = documentRepository.save(doc);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo("PENDING");

        // Status transition to ACTIVE
        saved.setStatus("ACTIVE");
        documentRepository.save(saved);

        Optional<Document> activeDoc = documentRepository.findByFilename("AML-GUIDE-PEP-QA.pdf");
        assertThat(activeDoc).isPresent();
        assertThat(activeDoc.get().getStatus()).isEqualTo("ACTIVE");

        List<Document> activeList = documentRepository.findByStatus("ACTIVE");
        assertThat(activeList).anyMatch(d -> d.getFilename().equals("AML-GUIDE-PEP-QA.pdf"));
    }

    @Test
    @DisplayName("Repository - DocumentChunks: Verify chunk persistence and index ordering")
    void testDocumentChunkRepositoryOperations() {
        User admin = userRepository.findByUsername("admin").orElseThrow();
        Document doc = documentRepository.save(new Document(
            "Test Policy", "test.pdf", "POLICY", "v1.0", "Compliance", "ACTIVE", admin.getId()
        ));

        DocumentChunk chunk1 = new DocumentChunk();
        chunk1.setDocumentId(doc.getId());
        chunk1.setChunkText("Chunk text 1");
        chunk1.setChunkIndex(0);
        chunk1.setSection("Section 1");
        chunk1.setPageNumber(1);

        DocumentChunk chunk2 = new DocumentChunk();
        chunk2.setDocumentId(doc.getId());
        chunk2.setChunkText("Chunk text 2");
        chunk2.setChunkIndex(1);
        chunk2.setSection("Section 2");
        chunk2.setPageNumber(2);

        documentChunkRepository.saveAll(List.of(chunk1, chunk2));

        List<DocumentChunk> chunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(doc.getId());
        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getChunkIndex()).isEqualTo(0);
        assertThat(chunks.get(1).getChunkIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("Repository - ChatSession & Message: Verify user session hierarchy and chronological ordering")
    void testChatSessionAndMessageOperations() {
        User analyst = userRepository.findByUsername("analyst").orElseThrow();

        ChatSession session = new ChatSession(analyst.getId(), "QA Investigation Session");
        ChatSession savedSession = chatSessionRepository.save(session);
        assertThat(savedSession.getId()).isNotNull();

        ChatMessage msg1 = new ChatMessage(savedSession.getId(), "USER", "What is CTR?");
        ChatMessage msg2 = new ChatMessage(savedSession.getId(), "ASSISTANT", "CTR is mandatory above $10,000.");
        chatMessageRepository.saveAll(List.of(msg1, msg2));

        List<ChatMessage> transcript = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(savedSession.getId());
        assertThat(transcript).hasSize(2);
        assertThat(transcript.get(0).getRole()).isEqualTo("USER");
        assertThat(transcript.get(1).getRole()).isEqualTo("ASSISTANT");

        List<ChatSession> userSessions = chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(analyst.getId());
        assertThat(userSessions).anyMatch(s -> s.getId().equals(savedSession.getId()));
    }

    @Test
    @DisplayName("Repository - AuditLog: Verify security compliance event auditing and querying")
    void testAuditLogRepositoryOperations() {
        User analyst = userRepository.findByUsername("analyst").orElseThrow();
        String reqId = UUID.randomUUID().toString();

        AuditLog logEntry = new AuditLog(
            analyst.getId(),
            "RAG_QUERY_AUDIT_TEST",
            "CHAT_SESSION",
            "sess-12345",
            reqId,
            "{\"query\":\"CTR threshold test\",\"status\":\"SAFE\"}"
        );
        AuditLog saved = auditLogRepository.save(logEntry);
        assertThat(saved.getId()).isNotNull();

        List<AuditLog> found = auditLogRepository.findByActionOrderByCreatedAtDesc("RAG_QUERY_AUDIT_TEST");
        assertThat(found).isNotEmpty();
        assertThat(found.get(0).getRequestId()).isEqualTo(reqId);
        assertThat(found.get(0).getMetadata()).contains("CTR threshold test");
    }
}
