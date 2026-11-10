package com.bank.aml.kafka;

import com.bank.aml.cache.TenantAwareCacheService;
import com.bank.aml.dto.request.DocumentUploadRequest;
import com.bank.aml.dto.response.DocumentResponse;
import com.bank.aml.entity.IngestionJob;
import com.bank.aml.entity.User;
import com.bank.aml.repository.DocumentRepository;
import com.bank.aml.repository.IngestionJobRepository;
import com.bank.aml.repository.UserRepository;
import com.bank.aml.security.JwtTokenProvider;
import com.bank.aml.security.SecurityUserPrincipal;
import com.bank.aml.security.rag.AuthorizedRetrievalService;
import com.bank.aml.security.tenant.TenantContext;
import com.bank.aml.service.DocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.isOneOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class Phase6KafkaAndRedisIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private IngestionJobRepository ingestionJobRepository;

    @Autowired
    private TenantAwareCacheService cacheService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User adminUser;
    private String adminToken;
    private SecurityUserPrincipal adminPrincipal;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        adminUser = userRepository.findByUsername("admin")
            .orElseGet(() -> {
                User u = new User();
                u.setUsername("admin");
                u.setEmail("admin@amlguardian.bank");
                u.setPasswordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG");
                u.setTenantId("default-bank");
                u.setEnabled(true);
                return userRepository.save(u);
            });

        adminPrincipal = new SecurityUserPrincipal(
            adminUser.getId(),
            adminUser.getUsername(),
            adminUser.getPasswordHash(),
            adminUser.isEnabled(),
            adminUser.getTenantId(),
            Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        adminToken = jwtTokenProvider.generateToken(adminPrincipal);
    }

    @Test
    @DisplayName("Verify document upload automatically generates an IngestionJob record with stage and progress tracking")
    void testUploadCreatesIngestionJob() {
        TenantContext.setTenantId("default-bank");
        String uniqueId = UUID.randomUUID().toString();

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "finra_aml_notice_" + uniqueId + ".txt",
            MediaType.TEXT_PLAIN_VALUE,
            ("FINRA Rule 3310 requires written AML compliance programs. Token: " + uniqueId).getBytes()
        );

        DocumentUploadRequest req = new DocumentUploadRequest(
            "FINRA Rule 3310 Notice " + uniqueId,
            "POLICY",
            "v1.0",
            "FINRA Regulatory Notice",
            "FINRA-" + uniqueId.substring(0, 8),
            "US",
            null,
            "APPROVED"
        );

        DocumentResponse doc = documentService.uploadDocument(file, req, adminUser.getId());
        assertThat(doc).isNotNull();
        assertThat(doc.id()).isNotNull();

        // Check that an IngestionJob was created
        var jobOpt = ingestionJobRepository.findTopByTenantIdAndDocumentIdOrderByCreatedAtDesc("default-bank", doc.id());
        assertThat(jobOpt).isPresent();

        IngestionJob job = jobOpt.get();
        assertThat(job.getDocumentId()).isEqualTo(doc.id());
        assertThat(job.getStatus()).isIn("COMPLETED", "PROCESSING", "QUEUED");
        assertThat(job.getProgressPct()).isGreaterThanOrEqualTo(5);
        assertThat(job.getCorrelationId()).isNotBlank();
    }

    @Test
    @DisplayName("Verify GET /api/v1/documents/jobs/{jobId} returns valid job lifecycle metadata")
    void testGetJobByIdEndpoint() throws Exception {
        TenantContext.setTenantId("default-bank");
        String uniqueId = UUID.randomUUID().toString();

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "fincen_guidance_" + uniqueId + ".txt",
            MediaType.TEXT_PLAIN_VALUE,
            ("FinCEN guidance on beneficial ownership reporting rules under CTA. Token: " + uniqueId).getBytes()
        );

        DocumentUploadRequest req = new DocumentUploadRequest(
            "Beneficial Ownership Guidance " + uniqueId,
            "POLICY",
            "v1.0",
            "FinCEN",
            "CTA-" + uniqueId.substring(0, 8),
            "US",
            null,
            "APPROVED"
        );

        DocumentResponse doc = documentService.uploadDocument(file, req, adminUser.getId());
        var job = ingestionJobRepository.findTopByTenantIdAndDocumentIdOrderByCreatedAtDesc("default-bank", doc.id()).orElseThrow();

        mockMvc.perform(get("/api/v1/documents/jobs/" + job.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(job.getId().toString()))
            .andExpect(jsonPath("$.documentId").value(doc.id().toString()))
            .andExpect(jsonPath("$.tenantId").value("default-bank"))
            .andExpect(jsonPath("$.jobType").value("DOCUMENT_INGESTION"))
            .andExpect(jsonPath("$.progressPct").value(job.getProgressPct()));
    }

    @Test
    @DisplayName("Verify GET /api/v1/documents/{id}/job returns latest job for a document")
    void testGetLatestJobForDocumentEndpoint() throws Exception {
        TenantContext.setTenantId("default-bank");
        String uniqueId = UUID.randomUUID().toString();

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "wolfsberg_principles_" + uniqueId + ".txt",
            MediaType.TEXT_PLAIN_VALUE,
            ("Wolfsberg AML Principles for Correspondent Banking. Token: " + uniqueId).getBytes()
        );

        DocumentUploadRequest req = new DocumentUploadRequest(
            "Wolfsberg Principles " + uniqueId,
            "POLICY",
            "v1.0",
            "Wolfsberg Group",
            "WOLFS-" + uniqueId.substring(0, 8),
            "GLOBAL",
            null,
            "APPROVED"
        );

        DocumentResponse doc = documentService.uploadDocument(file, req, adminUser.getId());

        mockMvc.perform(get("/api/v1/documents/" + doc.id() + "/job")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.documentId").value(doc.id().toString()))
            .andExpect(jsonPath("$.tenantId").value("default-bank"));
    }

    @Test
    @DisplayName("Verify POST /api/v1/documents/{id}/retry triggers job re-execution and retry tracking")
    void testRetryJobEndpoint() throws Exception {
        TenantContext.setTenantId("default-bank");
        String uniqueId = UUID.randomUUID().toString();

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "retry_target_" + uniqueId + ".txt",
            MediaType.TEXT_PLAIN_VALUE,
            ("High risk customers require enhanced due diligence (EDD). Token: " + uniqueId).getBytes()
        );

        DocumentUploadRequest req = new DocumentUploadRequest(
            "EDD Standard for Retry " + uniqueId,
            "POLICY",
            "v1.0",
            "Internal Compliance",
            "EDD-" + uniqueId.substring(0, 8),
            "GLOBAL",
            null,
            "APPROVED"
        );

        DocumentResponse doc = documentService.uploadDocument(file, req, adminUser.getId());

        mockMvc.perform(post("/api/v1/documents/" + doc.id() + "/retry")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.documentId").value(doc.id().toString()))
            .andExpect(jsonPath("$.retryCount").value(1))
            .andExpect(jsonPath("$.status").value(isOneOf("COMPLETED", "PROCESSING", "QUEUED")));
    }

    @Test
    @DisplayName("Verify tenant-aware cache isolation: cross-tenant access prohibited and evictions work as expected")
    void testTenantCacheIsolationAndEviction() {
        TenantContext.setTenantId("tenant-alpha");
        cacheService.put("retrieval", "aml-query-test", "Alpha Policy Context Result", Duration.ofMinutes(10));

        assertThat(cacheService.get("retrieval", "aml-query-test", String.class))
            .isPresent()
            .contains("Alpha Policy Context Result");

        // Switch to tenant-beta
        TenantContext.setTenantId("tenant-beta");
        assertThat(cacheService.get("retrieval", "aml-query-test", String.class))
            .as("Zero cross-tenant cache leakage must be strictly guaranteed")
            .isEmpty();

        // Evict tenant-alpha retrieval namespace
        TenantContext.setTenantId("tenant-alpha");
        cacheService.evictNamespace("retrieval");
        assertThat(cacheService.get("retrieval", "aml-query-test", String.class))
            .isEmpty();
    }
}
