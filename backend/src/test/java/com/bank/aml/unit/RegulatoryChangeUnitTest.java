package com.bank.aml.unit;

import com.bank.aml.dto.request.HumanValidationRequest;
import com.bank.aml.dto.response.PolicyFamilySummaryResponse;
import com.bank.aml.dto.response.PolicyVersionDetailResponse;
import com.bank.aml.dto.response.SectionDiffResponse;
import com.bank.aml.entity.ComplianceNotification;
import com.bank.aml.entity.Document;
import com.bank.aml.entity.DocumentChunk;
import com.bank.aml.entity.PolicySectionDiff;
import com.bank.aml.repository.ComplianceNotificationRepository;
import com.bank.aml.repository.DocumentChunkRepository;
import com.bank.aml.repository.DocumentRepository;
import com.bank.aml.repository.PolicySectionDiffRepository;
import com.bank.aml.security.SecurityUserPrincipal;
import com.bank.aml.security.rag.SecurityAuditService;
import com.bank.aml.security.tenant.TenantContext;
import com.bank.aml.service.RegulatoryChangeService;
import com.bank.aml.service.SectionComparisonService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RegulatoryChangeUnitTest {

    private DocumentRepository documentRepository;
    private DocumentChunkRepository documentChunkRepository;
    private PolicySectionDiffRepository diffRepository;
    private ComplianceNotificationRepository notificationRepository;
    private SectionComparisonService sectionComparisonService;
    private SecurityAuditService securityAuditService;
    private RegulatoryChangeService regulatoryChangeService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        documentRepository = mock(DocumentRepository.class);
        documentChunkRepository = mock(DocumentChunkRepository.class);
        diffRepository = mock(PolicySectionDiffRepository.class);
        notificationRepository = mock(ComplianceNotificationRepository.class);
        sectionComparisonService = mock(SectionComparisonService.class);
        securityAuditService = mock(SecurityAuditService.class);

        regulatoryChangeService = new RegulatoryChangeService(
            documentRepository,
            documentChunkRepository,
            diffRepository,
            notificationRepository,
            sectionComparisonService,
            securityAuditService,
            objectMapper
        );

        TenantContext.setTenantId("test-bank");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Policy Families: Groups policies by code, calculates version count and effective version")
    void testListPolicyFamilies() {
        when(documentRepository.findDistinctPolicyCodesByTenantId("test-bank")).thenReturn(List.of("AML-SOP-001"));

        Document v1 = createDoc("v1.0", "AML-SOP-001", false, "SUPERSEDED");
        Document v2 = createDoc("v2.0", "AML-SOP-001", true, "APPROVED");

        when(documentRepository.findByTenantIdAndPolicyCodeOrderByCreatedAtDesc("test-bank", "AML-SOP-001"))
            .thenReturn(List.of(v2, v1));

        List<PolicyFamilySummaryResponse> families = regulatoryChangeService.listPolicyFamilies();

        assertThat(families).hasSize(1);
        PolicyFamilySummaryResponse summary = families.get(0);
        assertThat(summary.policyCode()).isEqualTo("AML-SOP-001");
        assertThat(summary.effectiveVersion()).isEqualTo("v2.0");
        assertThat(summary.totalVersions()).isEqualTo(2);
        assertThat(summary.pendingApprovalCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Human Validation: Updates diff validation status and records forensic audit log")
    void testHumanValidationWorkflow() {
        UUID diffId = UUID.randomUUID();
        PolicySectionDiff diff = new PolicySectionDiff(
            "test-bank", "AML-SOP-001", UUID.randomUUID(), UUID.randomUUID(),
            "4.0 SAR", "MODIFIED_OBLIGATION", "Old text", "New text", "Modified", "[]"
        );
        diff.setId(diffId);

        when(diffRepository.findById(diffId)).thenReturn(Optional.of(diff));
        when(diffRepository.save(any(PolicySectionDiff.class))).thenAnswer(inv -> inv.getArgument(0));

        SecurityUserPrincipal user = mock(SecurityUserPrincipal.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(user.getUsername()).thenReturn("compliance_analyst");

        HumanValidationRequest req = new HumanValidationRequest("VALIDATED", "Approved threshold change verified against FinCEN advisory.");
        SectionDiffResponse response = regulatoryChangeService.validateDiff(diffId, req, user);

        assertThat(response.humanValidationStatus()).isEqualTo("VALIDATED");
        assertThat(response.requiresHumanValidation()).isFalse();
        assertThat(response.validationNotes()).contains("verified against FinCEN");

        verify(securityAuditService).recordSecurityEvent(
            eq(user.getId()),
            eq("POLICY_DIFF_HUMAN_VALIDATION"),
            eq("POLICY_DIFF"),
            eq(diffId.toString()),
            anyString(),
            anyMap()
        );
    }

    @Test
    @DisplayName("Policy Approval: Activates new version, supersedes prior version, and dispatches notifications")
    void testApprovePolicyVersionWorkflow() {
        UUID docId = UUID.randomUUID();
        Document newDoc = createDoc("v2.0", "AML-SOP-001", false, "PENDING_APPROVAL");
        newDoc.setId(docId);

        UUID priorDocId = UUID.randomUUID();
        Document priorDoc = createDoc("v1.0", "AML-SOP-001", true, "APPROVED");
        priorDoc.setId(priorDocId);

        when(documentRepository.findById(docId)).thenReturn(Optional.of(newDoc));
        when(documentRepository.findByTenantIdAndPolicyCodeOrderByCreatedAtDesc("test-bank", "AML-SOP-001"))
            .thenReturn(List.of(newDoc, priorDoc));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        DocumentChunk targetChunk = new DocumentChunk();
        targetChunk.setId(UUID.randomUUID());
        when(documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(docId)).thenReturn(List.of(targetChunk));

        DocumentChunk priorChunk = new DocumentChunk();
        priorChunk.setId(UUID.randomUUID());
        when(documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(priorDocId)).thenReturn(List.of(priorChunk));

        SecurityUserPrincipal admin = mock(SecurityUserPrincipal.class);
        when(admin.getId()).thenReturn(UUID.randomUUID());
        when(admin.getUsername()).thenReturn("admin");

        PolicyVersionDetailResponse approved = regulatoryChangeService.approvePolicyVersion(docId, admin);

        assertThat(approved.approvalStatus()).isEqualTo("APPROVED");
        assertThat(approved.isEffective()).isTrue();
        assertThat(priorDoc.getStatus()).isEqualTo("SUPERSEDED");
        assertThat(priorDoc.getIsEffective()).isFalse();
        assertThat(priorDoc.getSupersededBy()).isEqualTo(docId);

        // Chunks activation check
        assertThat(targetChunk.getIsEffective()).isTrue();
        assertThat(priorChunk.getIsEffective()).isFalse();

        // Notification dispatched check
        verify(notificationRepository).save(any(ComplianceNotification.class));
        verify(securityAuditService).recordSecurityEvent(
            eq(admin.getId()),
            eq("POLICY_VERSION_APPROVED"),
            eq("POLICY"),
            eq("AML-SOP-001"),
            anyString(),
            anyMap()
        );
    }

    private Document createDoc(String version, String code, boolean isEffective, String approvalStatus) {
        Document doc = new Document();
        doc.setId(UUID.randomUUID());
        doc.setTitle("Transaction Monitoring SOP");
        doc.setVersion(version);
        doc.setPolicyCode(code);
        doc.setTenantId("test-bank");
        doc.setIsEffective(isEffective);
        doc.setApprovalStatus(approvalStatus);
        doc.setEffectiveDate(OffsetDateTime.now());
        return doc;
    }
}
