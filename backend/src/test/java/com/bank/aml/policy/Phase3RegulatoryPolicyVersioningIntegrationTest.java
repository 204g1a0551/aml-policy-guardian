package com.bank.aml.policy;

import com.bank.aml.dto.request.DocumentUploadRequest;
import com.bank.aml.dto.request.HumanValidationRequest;
import com.bank.aml.dto.request.RetrievalFilterDto;
import com.bank.aml.dto.response.DocumentResponse;
import com.bank.aml.entity.ComplianceNotification;
import com.bank.aml.entity.Document;
import com.bank.aml.entity.PolicySectionDiff;
import com.bank.aml.entity.User;
import com.bank.aml.rag.ScoredChunk;
import com.bank.aml.repository.ComplianceNotificationRepository;
import com.bank.aml.repository.DocumentRepository;
import com.bank.aml.repository.PolicySectionDiffRepository;
import com.bank.aml.repository.UserRepository;
import com.bank.aml.security.JwtTokenProvider;
import com.bank.aml.security.SecurityUserPrincipal;
import com.bank.aml.security.rag.AuthorizedRetrievalService;
import com.bank.aml.security.tenant.TenantContext;
import com.bank.aml.service.DocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Phase3RegulatoryPolicyVersioningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private PolicySectionDiffRepository diffRepository;

    @Autowired
    private ComplianceNotificationRepository notificationRepository;

    @Autowired
    private AuthorizedRetrievalService authorizedRetrievalService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private User adminUser;
    private User analystUser;

    private String adminToken;
    private String analystToken;

    private SecurityUserPrincipal adminPrincipal;
    private SecurityUserPrincipal analystPrincipal;

    @BeforeEach
    void setUp() {
        adminUser = userRepository.findByUsername("admin").orElseThrow();
        analystUser = userRepository.findByUsername("analyst").orElseThrow();

        adminPrincipal = SecurityUserPrincipal.create(adminUser);
        analystPrincipal = SecurityUserPrincipal.create(analystUser);

        adminToken = jwtTokenProvider.generateToken(adminPrincipal);
        analystToken = jwtTokenProvider.generateToken(analystPrincipal);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("API: List policy families groups policies by code and reports effective version")
    void testListPolicyFamilies() throws Exception {
        mockMvc.perform(get("/api/v1/policies")
                .header("Authorization", "Bearer " + analystToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].policyCode").isNotEmpty())
            .andExpect(jsonPath("$[0].totalVersions").isNumber());
    }

    @Test
    @DisplayName("API: List versions of a specific policy code")
    void testListPolicyVersionsByCode() throws Exception {
        mockMvc.perform(get("/api/v1/policies/AML-SOP-002/versions")
                .header("Authorization", "Bearer " + analystToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].policyCode").value("AML-SOP-002"));
    }

    @Test
    @DisplayName("Versioning & Diff: Ingests 2 successive policy versions, generates side-by-side section diffs with obligation classification")
    void testPolicySideBySideComparison() throws Exception {
        String testPolicyCode = "BSA-POL-" + UUID.randomUUID().toString().substring(0, 6);
        String uniqueNonce = UUID.randomUUID().toString();

        // 1. Upload Base Version (v1.0)
        String v1Content = "### Section 1.0 Scope\nStandard scope " + uniqueNonce + ".\n\n### Section 2.0 CTR Filing\nCash transactions exceeding $10,000 must be filed within 15 days.";
        MockMultipartFile fileV1 = new MockMultipartFile(
            "file", testPolicyCode + "_v1.txt", "text/plain", v1Content.getBytes(StandardCharsets.UTF_8)
        );
        DocumentUploadRequest reqV1 = new DocumentUploadRequest(
            "Currency Reporting Policy", "POLICY", "v1.0", "BSA Regulatory Desk",
            testPolicyCode, "US-FinCEN", OffsetDateTime.now().minusDays(30), "APPROVED"
        );
        DocumentResponse docV1 = documentService.uploadDocument(fileV1, reqV1, adminPrincipal.getId());

        // 2. Upload Target Version (v2.0) with modified threshold & added section
        String v2Content = "### Section 1.0 Scope\nStandard scope " + uniqueNonce + ".\n\n### Section 2.0 CTR Filing\nCash transactions exceeding $5,000 must be filed within 7 calendar days.\n\n### Section 3.0 Crypto Exemptions\nMandatory reporting for virtual assets exceeding $3,000.";
        MockMultipartFile fileV2 = new MockMultipartFile(
            "file", testPolicyCode + "_v2.txt", "text/plain", v2Content.getBytes(StandardCharsets.UTF_8)
        );
        DocumentUploadRequest reqV2 = new DocumentUploadRequest(
            "Currency Reporting Policy", "POLICY", "v2.0", "BSA Regulatory Desk",
            testPolicyCode, "US-FinCEN", OffsetDateTime.now(), "PENDING_APPROVAL"
        );
        DocumentResponse docV2 = documentService.uploadDocument(fileV2, reqV2, adminPrincipal.getId());

        // 3. Call Compare API
        MvcResult result = mockMvc.perform(get("/api/v1/policies/compare")
                .param("baseDocumentId", docV1.id().toString())
                .param("targetDocumentId", docV2.id().toString())
                .header("Authorization", "Bearer " + analystToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.policyCode").value(testPolicyCode))
            .andExpect(jsonPath("$.baseVersion").value("v1.0"))
            .andExpect(jsonPath("$.targetVersion").value("v2.0"))
            .andExpect(jsonPath("$.metrics.totalSections").value(3))
            .andExpect(jsonPath("$.metrics.additionsCount").value(1))
            .andExpect(jsonPath("$.metrics.modifiedObligationsCount").value(1))
            .andExpect(jsonPath("$.metrics.unchangedCount").value(1))
            .andReturn();

        // 4. Validate Section 2.0 detected modification
        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).contains("Monetary threshold shifted");
        assertThat(responseBody).contains("Compliance timeline modified");
    }

    @Test
    @DisplayName("Human Validation: Compliance analyst validates AI-extracted policy change with review notes")
    void testHumanValidationOfDiff() throws Exception {
        Document existingDoc = documentRepository.findAll().stream().findFirst().orElseThrow();
        PolicySectionDiff diff = new PolicySectionDiff(
            "default-bank", "AML-TEST-VAL", existingDoc.getId(), existingDoc.getId(),
            "3.0 Alert Triage", "MODIFIED_OBLIGATION", "Old text", "New text", "Threshold updated", "[]"
        );
        PolicySectionDiff saved = diffRepository.save(diff);

        HumanValidationRequest req = new HumanValidationRequest("VALIDATED", "Analyst confirmed obligation shift against FinCEN notice.");
        String reqJson = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/v1/policies/diff/" + saved.getId() + "/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reqJson)
                .header("Authorization", "Bearer " + analystToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.humanValidationStatus").value("VALIDATED"))
            .andExpect(jsonPath("$.requiresHumanValidation").value(false))
            .andExpect(jsonPath("$.validationNotes").value("Analyst confirmed obligation shift against FinCEN notice."));

        PolicySectionDiff reloaded = diffRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getHumanValidationStatus()).isEqualTo("VALIDATED");
        assertThat(reloaded.getValidatedBy()).isEqualTo(analystPrincipal.getId());
    }

    @Test
    @DisplayName("Approval Workflow: Approving new policy version marks it effective, supersedes old version, and emits notification")
    void testApprovePolicyVersionWorkflow() throws Exception {
        String code = "APPROVAL-TEST-" + UUID.randomUUID().toString().substring(0, 6);
        String uniqueNonce = UUID.randomUUID().toString();

        // Upload v1 (currently effective)
        MockMultipartFile f1 = new MockMultipartFile("file", code + "_v1.txt", "text/plain", ("Policy v1 content " + uniqueNonce).getBytes(StandardCharsets.UTF_8));
        DocumentUploadRequest req1 = new DocumentUploadRequest("Test Policy", "SOP", "v1.0", "Desk", code, "GLOBAL", OffsetDateTime.now().minusDays(10), "APPROVED");
        DocumentResponse doc1 = documentService.uploadDocument(f1, req1, adminPrincipal.getId());

        // Upload v2 (draft / pending approval)
        MockMultipartFile f2 = new MockMultipartFile("file", code + "_v2.txt", "text/plain", ("Policy v2 content updated " + uniqueNonce).getBytes(StandardCharsets.UTF_8));
        DocumentUploadRequest req2 = new DocumentUploadRequest("Test Policy", "SOP", "v2.0", "Desk", code, "GLOBAL", OffsetDateTime.now(), "PENDING_APPROVAL");
        DocumentResponse doc2 = documentService.uploadDocument(f2, req2, adminPrincipal.getId());

        // Call Approve API as Admin
        mockMvc.perform(post("/api/v1/policies/" + doc2.id() + "/approve")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.approvalStatus").value("APPROVED"))
            .andExpect(jsonPath("$.isEffective").value(true));

        // Check doc1 is now superseded
        Document updatedDoc1 = documentRepository.findById(doc1.id()).orElseThrow();
        assertThat(updatedDoc1.getStatus()).isEqualTo("SUPERSEDED");
        assertThat(updatedDoc1.getIsEffective()).isFalse();
        assertThat(updatedDoc1.getSupersededBy()).isEqualTo(doc2.id());

        // Check doc2 is now effective
        Document updatedDoc2 = documentRepository.findById(doc2.id()).orElseThrow();
        assertThat(updatedDoc2.getIsEffective()).isTrue();
        assertThat(updatedDoc2.getApprovedBy()).isEqualTo(adminPrincipal.getId());

        // Check notification was saved for analysts
        List<ComplianceNotification> notifications = notificationRepository.findByTenantIdAndRecipientRoleAndIsReadFalseOrderByCreatedAtDesc("default-bank", "ROLE_COMPLIANCE_ANALYST");
        assertThat(notifications).anyMatch(n -> n.getPolicyCode().equals(code));
    }

    @Test
    @DisplayName("Notifications: Compliance analyst views and marks notifications as read")
    void testComplianceNotificationLifecycle() throws Exception {
        ComplianceNotification notif = new ComplianceNotification(
            "default-bank", "ROLE_COMPLIANCE_ANALYST", null,
            "Urgent Policy Update", "AML-SOP-001 has been updated to v3.0.", "AML-SOP-001", null
        );
        ComplianceNotification saved = notificationRepository.save(notif);

        // List notifications
        mockMvc.perform(get("/api/v1/policies/notifications")
                .header("Authorization", "Bearer " + analystToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].title").isNotEmpty());

        // Mark read
        mockMvc.perform(put("/api/v1/policies/notifications/" + saved.getId() + "/read")
                .header("Authorization", "Bearer " + analystToken))
            .andExpect(status().isNoContent());

        ComplianceNotification reloaded = notificationRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getIsRead()).isTrue();
    }

    @Test
    @DisplayName("Version-Aware Retrieval: RAG retrieval with onlyEffective=true retrieves only currently active policy version")
    void testVersionAwareRetrievalFilter() {
        String code = "RAG-VER-" + UUID.randomUUID().toString().substring(0, 6);

        // 1. Upload v1.0 (superseded)
        MockMultipartFile f1 = new MockMultipartFile("file", code + "_v1.txt", "text/plain", ("Specific historical guidance " + code + " for 2022.").getBytes(StandardCharsets.UTF_8));
        DocumentUploadRequest req1 = new DocumentUploadRequest("Historical Policy", "POLICY", "v1.0", "Source", code, "US", OffsetDateTime.now().minusYears(2), "SUPERSEDED");
        DocumentResponse doc1 = documentService.uploadDocument(f1, req1, adminPrincipal.getId());

        // Explicitly set v1 as not effective
        Document d1 = documentRepository.findById(doc1.id()).orElseThrow();
        d1.setIsEffective(false);
        d1.setStatus("SUPERSEDED");
        documentRepository.save(d1);

        // 2. Upload v2.0 (effective)
        MockMultipartFile f2 = new MockMultipartFile("file", code + "_v2.txt", "text/plain", ("Specific authoritative current guidance " + code + " for 2026.").getBytes(StandardCharsets.UTF_8));
        DocumentUploadRequest req2 = new DocumentUploadRequest("Effective Policy", "POLICY", "v2.0", "Source", code, "US", OffsetDateTime.now(), "APPROVED");
        DocumentResponse doc2 = documentService.uploadDocument(f2, req2, adminPrincipal.getId());

        // Retrieval with onlyEffective = true
        RetrievalFilterDto effectiveFilter = new RetrievalFilterDto(null, null, null, "VECTOR_ONLY", code, "US", true, null);
        List<ScoredChunk> effectiveResults = authorizedRetrievalService.retrieveAuthorizedContext(code, analystPrincipal, effectiveFilter);

        assertThat(effectiveResults).isNotEmpty();
        assertThat(effectiveResults).allMatch(c -> c.getDocumentTitle().contains("Effective Policy"));
        assertThat(effectiveResults).noneMatch(c -> c.getDocumentTitle().contains("Historical Policy"));
    }

    @Test
    @DisplayName("Tenant Isolation: Cross-tenant policy diff access is rejected with 403 Forbidden")
    void testCrossTenantPolicyComparisonBlocked() throws Exception {
        TenantContext.setTenantId("tenant-alpha");
        MockMultipartFile f1 = new MockMultipartFile("file", "Alpha_Doc1_" + UUID.randomUUID() + ".txt", "text/plain", ("Alpha proprietary policy " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
        DocumentUploadRequest req1 = new DocumentUploadRequest("Alpha Policy", "POLICY", "v1.0", "Alpha");
        DocumentResponse docAlpha = documentService.uploadDocument(f1, req1, adminPrincipal.getId());

        // Switch to tenant-beta
        TenantContext.setTenantId("tenant-beta");

        // Attempting to compare using docAlpha under tenant-beta must fail
        mockMvc.perform(get("/api/v1/policies/compare")
                .param("baseDocumentId", docAlpha.id().toString())
                .param("targetDocumentId", docAlpha.id().toString())
                .header("X-Tenant-ID", "tenant-beta")
                .header("Authorization", "Bearer " + analystToken))
            .andExpect(status().isForbidden());
    }
}
