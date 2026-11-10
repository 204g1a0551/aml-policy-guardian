package com.bank.aml.unit;

import com.bank.aml.dto.request.AnalystDecisionRequest;
import com.bank.aml.dto.request.CreateAlertRequest;
import com.bank.aml.dto.request.ReviewerDecisionRequest;
import com.bank.aml.dto.request.UpdateInvestigationDraftRequest;
import com.bank.aml.dto.response.AmlAlertResponse;
import com.bank.aml.dto.response.AmlInvestigationDetailResponse;
import com.bank.aml.dto.response.InvestigationExportResponse;
import com.bank.aml.entity.AmlAlert;
import com.bank.aml.entity.AmlInvestigation;
import com.bank.aml.entity.AmlTransaction;
import com.bank.aml.rag.EvidenceQualityEvaluator;
import com.bank.aml.repository.AmlAlertRepository;
import com.bank.aml.repository.AmlInvestigationRepository;
import com.bank.aml.repository.AmlTransactionRepository;
import com.bank.aml.repository.AuditLogRepository;
import com.bank.aml.repository.InvestigationTimelineRepository;
import com.bank.aml.security.SecurityUserPrincipal;
import com.bank.aml.security.rag.AuthorizedRetrievalService;
import com.bank.aml.security.rag.SecurityAuditService;
import com.bank.aml.service.InvestigationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InvestigationServiceUnitTest {

    private AmlAlertRepository alertRepository;
    private AmlTransactionRepository transactionRepository;
    private AmlInvestigationRepository investigationRepository;
    private InvestigationTimelineRepository timelineRepository;
    private AuthorizedRetrievalService retrievalService;
    private EvidenceQualityEvaluator evidenceQualityEvaluator;
    private SecurityAuditService securityAuditService;
    private AuditLogRepository auditLogRepository;
    private ObjectMapper objectMapper;
    private InvestigationService investigationService;

    private SecurityUserPrincipal analystUser;
    private SecurityUserPrincipal adminUser;

    @BeforeEach
    void setUp() {
        alertRepository = mock(AmlAlertRepository.class);
        transactionRepository = mock(AmlTransactionRepository.class);
        investigationRepository = mock(AmlInvestigationRepository.class);
        timelineRepository = mock(InvestigationTimelineRepository.class);
        retrievalService = mock(AuthorizedRetrievalService.class);
        evidenceQualityEvaluator = mock(EvidenceQualityEvaluator.class);
        securityAuditService = mock(SecurityAuditService.class);
        auditLogRepository = mock(AuditLogRepository.class);
        objectMapper = new ObjectMapper();

        investigationService = new InvestigationService(
            alertRepository,
            transactionRepository,
            investigationRepository,
            timelineRepository,
            retrievalService,
            evidenceQualityEvaluator,
            securityAuditService,
            auditLogRepository,
            objectMapper
        );

        analystUser = new SecurityUserPrincipal(
            UUID.randomUUID(), "analyst", "pass", true, "default",
            List.of(new SimpleGrantedAuthority("ROLE_COMPLIANCE_ANALYST"))
        );

        adminUser = new SecurityUserPrincipal(
            UUID.randomUUID(), "admin", "pass", true, "default",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }

    @Test
    @DisplayName("Generate synthetic alert should persist alert and 3 transactions with audit logging")
    void testGenerateSyntheticAlert() {
        CreateAlertRequest req = new CreateAlertRequest(
            "TM-RULE-101", "Cash Structuring", "CUST-100", "Acme Trading",
            "HIGH", "ACCT-999", BigDecimal.valueOf(28500.00), "USD", "HIGH"
        );

        when(alertRepository.save(any(AmlAlert.class))).thenAnswer(inv -> {
            AmlAlert a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        AmlAlertResponse resp = investigationService.generateSyntheticAlert(req, analystUser);

        assertNotNull(resp);
        assertEquals("TM-RULE-101", resp.scenarioCode());
        assertEquals(BigDecimal.valueOf(28500.00), resp.totalAlertAmount());
        assertEquals("NEW", resp.status());

        verify(transactionRepository, times(1)).saveAll(any());
        verify(securityAuditService, times(1)).recordSecurityEvent(any(), any(), eq("SYNTHETIC_ALERT_GENERATED"), eq("AML_ALERT"), any(), any(), any());
    }

    @Test
    @DisplayName("Analyze alert produces evidence-backed summary, policy citations, and SAR draft")
    void testAnalyzeAlertProducesEvidenceBackedDraft() {
        UUID alertId = UUID.randomUUID();
        AmlAlert alert = new AmlAlert(
            "ALRT-2026-1001", "TM-RULE-101", "Cash Structuring", "CUST-100",
            "Acme Trading", "HIGH", "ACCT-999", BigDecimal.valueOf(28500.00),
            "USD", "HIGH", "NEW", OffsetDateTime.now(), "default"
        );
        alert.setId(alertId);

        AmlTransaction txn = new AmlTransaction(
            alert, "TXN-001", OffsetDateTime.now(), BigDecimal.valueOf(9500.00),
            "USD", "CASH_DEPOSIT", "Teller", "USA", "CASH", "Cash deposit", true
        );

        when(alertRepository.findByIdAndTenantId(eq(alertId), any())).thenReturn(Optional.of(alert));
        when(investigationRepository.findByAlertIdAndTenantId(eq(alertId), any())).thenReturn(Optional.empty());
        when(transactionRepository.findByAlertIdOrderByTransactionDateDesc(eq(alertId))).thenReturn(List.of(txn));
        when(investigationRepository.save(any(AmlInvestigation.class))).thenAnswer(inv -> {
            AmlInvestigation i = inv.getArgument(0);
            if (i.getId() == null) {
                i.setId(UUID.randomUUID());
            }
            return i;
        });

        AmlInvestigationDetailResponse response = investigationService.analyzeAlert(alertId, analystUser);

        assertNotNull(response);
        assertEquals("DRAFT_READY", response.status());
        assertEquals("FILE_SAR", response.decisionRecommendation());
        assertNotNull(response.aiSummary());
        assertTrue(response.aiSummary().contains("TM-RULE-101"));
        assertNotNull(response.sarNarrativeDraft());
        assertTrue(response.sarNarrativeDraft().contains("SECTION I: SUBJECT & ACCOUNT INFORMATION"));
        assertTrue(response.sarNarrativeDraft().contains("SECTION V: PRELIMINARY INVESTIGATIVE FINDINGS"));
        assertNotNull(response.linkedPolicies());
        assertFalse(response.linkedPolicies().isEmpty());

        verify(timelineRepository, times(1)).save(any());
        verify(securityAuditService, times(1)).recordSecurityEvent(any(), any(), eq("INVESTIGATION_ANALYSIS_GENERATED"), eq("AML_INVESTIGATION"), any(), any(), any());
    }

    @Test
    @DisplayName("Analyst draft edit updates narrative text and records timeline")
    void testUpdateDraft() {
        UUID invId = UUID.randomUUID();
        AmlAlert alert = new AmlAlert("ALRT-1", "SC-1", "Title", "C-1", "Name", "HIGH", "A-1", BigDecimal.TEN, "USD", "HIGH", "DRAFT_GENERATED", OffsetDateTime.now(), "default");
        AmlInvestigation inv = new AmlInvestigation(alert, "INV-1", "analyst", "DRAFT_READY", "default");
        inv.setId(invId);

        when(investigationRepository.findByIdAndTenantId(eq(invId), any())).thenReturn(Optional.of(inv));
        when(investigationRepository.save(any(AmlInvestigation.class))).thenAnswer(i -> i.getArgument(0));

        UpdateInvestigationDraftRequest req = new UpdateInvestigationDraftRequest(
            "Updated Analyst Edited Narrative Draft...",
            "Verified counterparty UBO documents manually."
        );

        AmlInvestigationDetailResponse res = investigationService.updateDraft(invId, req, analystUser);

        assertNotNull(res);
        assertEquals("EDITED", res.analystDecision());
        assertEquals("Updated Analyst Edited Narrative Draft...", res.sarNarrativeDraft());
        assertEquals("Verified counterparty UBO documents manually.", res.analystNotes());

        verify(timelineRepository, times(1)).save(any());
        verify(securityAuditService, times(1)).recordSecurityEvent(any(), any(), eq("INVESTIGATION_DRAFT_MODIFIED"), eq("AML_INVESTIGATION"), any(), any(), any());
    }

    @Test
    @DisplayName("Analyst decision ACCEPT transitions investigation to UNDER_REVIEW")
    void testSubmitAnalystDecisionAccept() {
        UUID invId = UUID.randomUUID();
        AmlAlert alert = new AmlAlert("ALRT-1", "SC-1", "Title", "C-1", "Name", "HIGH", "A-1", BigDecimal.TEN, "USD", "HIGH", "DRAFT_GENERATED", OffsetDateTime.now(), "default");
        AmlInvestigation inv = new AmlInvestigation(alert, "INV-1", "analyst", "DRAFT_READY", "default");
        inv.setId(invId);

        when(investigationRepository.findByIdAndTenantId(eq(invId), any())).thenReturn(Optional.of(inv));
        when(investigationRepository.save(any(AmlInvestigation.class))).thenAnswer(i -> i.getArgument(0));

        AnalystDecisionRequest req = new AnalystDecisionRequest("ACCEPTED", null, "Draft accepted without amendments.");
        AmlInvestigationDetailResponse res = investigationService.submitAnalystDecision(invId, req, analystUser);

        assertNotNull(res);
        assertEquals("UNDER_REVIEW", res.status());
        assertEquals("ACCEPTED", res.analystDecision());
        assertEquals("IN_INVESTIGATION", alert.getStatus());
    }

    @Test
    @DisplayName("Reviewer approval transitions status to APPROVED and alert to APPROVED_FOR_FILING")
    void testSubmitReviewerDecisionApproval() {
        UUID invId = UUID.randomUUID();
        AmlAlert alert = new AmlAlert("ALRT-1", "SC-1", "Title", "C-1", "Name", "HIGH", "A-1", BigDecimal.TEN, "USD", "HIGH", "IN_INVESTIGATION", OffsetDateTime.now(), "default");
        AmlInvestigation inv = new AmlInvestigation(alert, "INV-1", "analyst", "UNDER_REVIEW", "default");
        inv.setId(invId);

        when(investigationRepository.findByIdAndTenantId(eq(invId), any())).thenReturn(Optional.of(inv));
        when(investigationRepository.save(any(AmlInvestigation.class))).thenAnswer(i -> i.getArgument(0));

        ReviewerDecisionRequest req = new ReviewerDecisionRequest("APPROVED", "Concur with findings. Cleared for FinCEN filing.", null);
        AmlInvestigationDetailResponse res = investigationService.submitReviewerDecision(invId, req, adminUser);

        assertNotNull(res);
        assertEquals("APPROVED", res.status());
        assertEquals("APPROVED", res.reviewerDecision());
        assertEquals("APPROVED_FOR_FILING", alert.getStatus());
        assertNotNull(res.completedAt());
    }

    @Test
    @DisplayName("Reviewer escalation sets status to ESCALATED with recorded escalation rationale")
    void testSubmitReviewerDecisionEscalation() {
        UUID invId = UUID.randomUUID();
        AmlAlert alert = new AmlAlert("ALRT-1", "SC-1", "Title", "C-1", "Name", "HIGH", "A-1", BigDecimal.TEN, "USD", "HIGH", "IN_INVESTIGATION", OffsetDateTime.now(), "default");
        AmlInvestigation inv = new AmlInvestigation(alert, "INV-1", "analyst", "UNDER_REVIEW", "default");
        inv.setId(invId);

        when(investigationRepository.findByIdAndTenantId(eq(invId), any())).thenReturn(Optional.of(inv));
        when(investigationRepository.save(any(AmlInvestigation.class))).thenAnswer(i -> i.getArgument(0));

        ReviewerDecisionRequest req = new ReviewerDecisionRequest(
            "ESCALATED_TO_MLRO",
            "Urgent MLRO review required due to foreign PEP nexus.",
            "Potential high-level diplomatic sensitivity under OFAC sanctions."
        );
        AmlInvestigationDetailResponse res = investigationService.submitReviewerDecision(invId, req, adminUser);

        assertNotNull(res);
        assertEquals("ESCALATED", res.status());
        assertEquals("ESCALATED", alert.getStatus());
        assertEquals("Potential high-level diplomatic sensitivity under OFAC sanctions.", res.escalationReason());
    }

    @Test
    @DisplayName("Export report contains complete dossier structure and mandatory human-in-the-loop legal disclaimer")
    void testExportReport() {
        UUID invId = UUID.randomUUID();
        AmlAlert alert = new AmlAlert("ALRT-2026-1001", "TM-RULE-101", "Cash Structuring", "CUST-100", "Acme Trading", "HIGH", "ACCT-999", BigDecimal.valueOf(28500.00), "USD", "HIGH", "APPROVED_FOR_FILING", OffsetDateTime.now(), "default");
        AmlInvestigation inv = new AmlInvestigation(alert, "INV-2026-1001", "analyst", "APPROVED", "default");
        inv.setId(invId);
        inv.setAiSummary("Automated surveillance identified 3 structured cash deposits.");
        inv.setSarNarrativeDraft("Section I: Subject Profile...");
        inv.setDecisionRecommendation("FILE_SAR");
        inv.setReviewer("compliance_manager");
        inv.setReviewerDecision("APPROVED");

        when(investigationRepository.findByIdAndTenantId(eq(invId), any())).thenReturn(Optional.of(inv));

        InvestigationExportResponse export = investigationService.exportReport(invId, adminUser);

        assertNotNull(export);
        assertEquals("INV-2026-1001", export.investigationReference());
        assertNotNull(export.markdownContent());
        assertTrue(export.markdownContent().contains("# AML INVESTIGATION COMPLIANCE REPORT"));
        assertTrue(export.markdownContent().contains("## 1. Subject Profile"));
        assertTrue(export.markdownContent().contains("MANDATORY LEGAL DISCLAIMER"));
        assertTrue(export.legalDisclaimer().contains("does NOT constitute an automated SAR filing decision"));
    }
}
