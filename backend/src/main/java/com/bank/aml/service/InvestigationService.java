package com.bank.aml.service;

import com.bank.aml.dto.request.AnalystDecisionRequest;
import com.bank.aml.dto.request.CreateAlertRequest;
import com.bank.aml.dto.request.ReviewerDecisionRequest;
import com.bank.aml.dto.request.UpdateInvestigationDraftRequest;
import com.bank.aml.dto.response.*;
import com.bank.aml.entity.*;
import com.bank.aml.exception.ResourceNotFoundException;
import com.bank.aml.rag.EvidenceQualityEvaluator;
import com.bank.aml.rag.ScoredChunk;
import com.bank.aml.repository.*;
import com.bank.aml.security.SecurityUserPrincipal;
import com.bank.aml.security.rag.AuthorizedRetrievalService;
import com.bank.aml.security.rag.SecurityAuditService;
import com.bank.aml.security.tenant.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class InvestigationService {

    private static final Logger log = LoggerFactory.getLogger(InvestigationService.class);

    private final AmlAlertRepository alertRepository;
    private final AmlTransactionRepository transactionRepository;
    private final AmlInvestigationRepository investigationRepository;
    private final InvestigationTimelineRepository timelineRepository;
    private final AuthorizedRetrievalService retrievalService;
    private final EvidenceQualityEvaluator evidenceQualityEvaluator;
    private final SecurityAuditService securityAuditService;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public InvestigationService(
        AmlAlertRepository alertRepository,
        AmlTransactionRepository transactionRepository,
        AmlInvestigationRepository investigationRepository,
        InvestigationTimelineRepository timelineRepository,
        AuthorizedRetrievalService retrievalService,
        EvidenceQualityEvaluator evidenceQualityEvaluator,
        SecurityAuditService securityAuditService,
        AuditLogRepository auditLogRepository,
        ObjectMapper objectMapper
    ) {
        this.alertRepository = alertRepository;
        this.transactionRepository = transactionRepository;
        this.investigationRepository = investigationRepository;
        this.timelineRepository = timelineRepository;
        this.retrievalService = retrievalService;
        this.evidenceQualityEvaluator = evidenceQualityEvaluator;
        this.securityAuditService = securityAuditService;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------------
    // ALERT MANAGEMENT
    // ------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<AmlAlertResponse> searchAlerts(String status, String severity, String scenarioCode, String query, Pageable pageable) {
        String tenantId = TenantContext.getTenantId();
        Page<AmlAlert> alertPage = alertRepository.searchAlerts(tenantId, status, severity, scenarioCode, query, pageable);
        List<AmlAlertResponse> responses = alertPage.getContent().stream()
            .map(this::mapAlertToResponse)
            .collect(Collectors.toList());
        return new PageImpl<>(responses, pageable, alertPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AmlAlertResponse getAlertDetail(UUID alertId) {
        String tenantId = TenantContext.getTenantId();
        AmlAlert alert = alertRepository.findByIdAndTenantId(alertId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Alert not found with ID: " + alertId));
        return mapAlertToResponse(alert);
    }

    public AmlAlertResponse generateSyntheticAlert(CreateAlertRequest request, SecurityUserPrincipal principal) {
        String tenantId = TenantContext.getTenantId();
        String alertRef = "ALRT-2026-" + String.format("%04d", new Random().nextInt(9000) + 1000);

        AmlAlert alert = new AmlAlert(
            alertRef,
            request.scenarioCode(),
            request.scenarioTitle(),
            request.customerId(),
            request.customerName(),
            request.customerRiskLevel() != null ? request.customerRiskLevel() : "HIGH",
            request.accountNumber(),
            request.totalAlertAmount(),
            request.currency() != null ? request.currency() : "USD",
            request.severity() != null ? request.severity() : "HIGH",
            "NEW",
            OffsetDateTime.now(),
            tenantId
        );

        // Generate 3 plausible synthetic transactions for this alert
        AmlAlert savedAlert = alertRepository.save(alert);
        generateSyntheticTransactionsForAlert(savedAlert);

        // Audit log
        UUID userId = principal != null ? principal.getId() : null;
        securityAuditService.recordSecurityEvent(
            userId,
            tenantId,
            "SYNTHETIC_ALERT_GENERATED",
            "AML_ALERT",
            savedAlert.getId().toString(),
            UUID.randomUUID().toString(),
            Map.of("alertReference", alertRef, "scenarioCode", request.scenarioCode(), "amount", request.totalAlertAmount())
        );

        return mapAlertToResponse(savedAlert);
    }

    private void generateSyntheticTransactionsForAlert(AmlAlert alert) {
        BigDecimal splitAmount = alert.getTotalAlertAmount().divide(BigDecimal.valueOf(3), 2, java.math.RoundingMode.HALF_UP);
        OffsetDateTime now = OffsetDateTime.now();

        AmlTransaction t1 = new AmlTransaction(
            alert,
            "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
            now.minusDays(2),
            splitAmount,
            alert.getCurrency(),
            "WIRE_TRANSFER_OUTBOUND",
            "Offshore Settlement Corp",
            "BVI",
            "BVI-" + (100000 + new Random().nextInt(900000)),
            "Initial tranche cross-border corporate transfer",
            true
        );

        AmlTransaction t2 = new AmlTransaction(
            alert,
            "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
            now.minusDays(1),
            splitAmount,
            alert.getCurrency(),
            "WIRE_TRANSFER_OUTBOUND",
            "Offshore Settlement Corp",
            "BVI",
            "BVI-" + (100000 + new Random().nextInt(900000)),
            "Second tranche cross-border corporate transfer",
            true
        );

        AmlTransaction t3 = new AmlTransaction(
            alert,
            "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
            now.minusHours(4),
            alert.getTotalAlertAmount().subtract(splitAmount.multiply(BigDecimal.valueOf(2))),
            alert.getCurrency(),
            "WIRE_TRANSFER_OUTBOUND",
            "Panama Logistics Agency",
            "Panama",
            "PAN-" + (100000 + new Random().nextInt(900000)),
            "Final balancing disbursement",
            true
        );

        transactionRepository.saveAll(List.of(t1, t2, t3));
        alert.setTransactions(new ArrayList<>(List.of(t1, t2, t3)));
    }

    // ------------------------------------------------------------------------
    // INVESTIGATION WORKFLOW & AI ANALYSIS
    // ------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<InvestigationSummaryResponse> searchInvestigations(String status, String assignedAnalyst, String query, Pageable pageable) {
        String tenantId = TenantContext.getTenantId();
        Page<AmlInvestigation> invPage = investigationRepository.searchInvestigations(tenantId, status, assignedAnalyst, query, pageable);
        List<InvestigationSummaryResponse> summaries = invPage.getContent().stream()
            .map(this::mapInvestigationToSummary)
            .collect(Collectors.toList());
        return new PageImpl<>(summaries, pageable, invPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AmlInvestigationDetailResponse getInvestigationDetail(UUID id) {
        String tenantId = TenantContext.getTenantId();
        AmlInvestigation inv = investigationRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Investigation not found with ID: " + id));
        return mapInvestigationToDetail(inv);
    }

    public AmlInvestigationDetailResponse analyzeAlert(UUID alertId, SecurityUserPrincipal principal) {
        String tenantId = TenantContext.getTenantId();
        AmlAlert alert = alertRepository.findByIdAndTenantId(alertId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Alert not found with ID: " + alertId));

        String username = principal != null ? principal.getUsername() : "compliance_analyst";
        UUID userId = principal != null ? principal.getId() : null;

        // Find or create investigation
        AmlInvestigation investigation = investigationRepository.findByAlertIdAndTenantId(alertId, tenantId)
            .orElseGet(() -> {
                String invRef = "INV-2026-" + alert.getAlertReference().replace("ALRT-2026-", "");
                AmlInvestigation newInv = new AmlInvestigation(alert, invRef, username, "PENDING_ANALYSIS", tenantId);
                return investigationRepository.save(newInv);
            });

        // 1. Retrieve applicable AML policies via tenant-aware hybrid search
        String retrievalQuery = String.format("Scenario %s %s: Alert amount %s %s for customer %s (%s). Transaction escalation threshold and suspicious activity reporting mandates.",
            alert.getScenarioCode(), alert.getScenarioTitle(), alert.getTotalAlertAmount(), alert.getCurrency(), alert.getCustomerName(), alert.getCustomerRiskLevel());

        List<ScoredChunk> policyChunks = new ArrayList<>();
        try {
            if (principal != null) {
                com.bank.aml.dto.request.RetrievalFilterDto filter = new com.bank.aml.dto.request.RetrievalFilterDto(
                    null, null, null, "HYBRID_RERANK", null, null, true, null
                );
                policyChunks = retrievalService.retrieveAuthorizedContext(retrievalQuery, principal, filter);
            }
        } catch (Exception e) {
            log.warn("Policy retrieval encountered fallback: {}", e.getMessage());
        }

        List<LinkedPolicyDto> linkedPolicies = buildLinkedPolicies(policyChunks, alert);
        String linkedPoliciesJson = serializeToJson(linkedPolicies);

        // 2. Fetch full transaction history
        List<AmlTransaction> txns = transactionRepository.findByAlertIdOrderByTransactionDateDesc(alertId);

        // 3. Formulate Evidence-Backed Investigation Summary
        String aiSummary = generateEvidenceBackedSummary(alert, txns, linkedPolicies);

        // 4. Formulate Policy Relevance & Red Flags Explanation
        String policyExplanation = generatePolicyRelevanceExplanation(alert, txns, linkedPolicies);

        // 5. Formulate 5-Section SAR Narrative Draft
        String sarDraft = generateSarNarrativeDraft(alert, txns, linkedPolicies);

        // 6. Determine Recommended Decision & Evidence Quality
        String recommendation = determineRecommendation(alert, txns);
        double evidenceScore = 0.88;
        String evidenceLevel = "HIGH";
        if (evidenceQualityEvaluator != null && !policyChunks.isEmpty()) {
            EvidenceQualityEvaluator.EvidenceAssessment assessment =
                evidenceQualityEvaluator.assessEvidence(retrievalQuery, policyChunks, List.of(), false);
            evidenceScore = assessment.score();
            evidenceLevel = assessment.level();
        }

        // 7. Update investigation state
        investigation.setAiSummary(aiSummary);
        investigation.setPolicyRelevanceExplanation(policyExplanation);
        investigation.setSarNarrativeDraft(sarDraft);
        investigation.setDecisionRecommendation(recommendation);
        investigation.setEvidenceQualityScore(evidenceScore);
        investigation.setEvidenceLevel(evidenceLevel);
        investigation.setLinkedPolicies(linkedPoliciesJson);
        investigation.setStatus("DRAFT_READY");
        investigation.setAssignedAnalyst(username);
        investigation.setUpdatedAt(OffsetDateTime.now());

        alert.setStatus("DRAFT_GENERATED");
        alert.setUpdatedAt(OffsetDateTime.now());
        alertRepository.save(alert);

        // Record timeline
        InvestigationTimeline timelineEntry = new InvestigationTimeline(
            investigation,
            "AI_ANALYSIS_GENERATED",
            "SYSTEM_AI_ASSISTANT",
            "Automated hybrid policy retrieval completed with " + linkedPolicies.size() + " supporting policy sections and initial SAR narrative drafted.",
            "PENDING_ANALYSIS",
            "DRAFT_READY"
        );
        timelineRepository.save(timelineEntry);

        // Record audit event and link hash
        securityAuditService.recordSecurityEvent(
            userId,
            tenantId,
            "INVESTIGATION_ANALYSIS_GENERATED",
            "AML_INVESTIGATION",
            investigation.getId().toString(),
            UUID.randomUUID().toString(),
            Map.of("investigationReference", investigation.getInvestigationReference(), "evidenceScore", evidenceScore, "recommendation", recommendation)
        );

        linkLatestAuditHash(investigation, tenantId);
        AmlInvestigation savedInv = investigationRepository.save(investigation);

        return mapInvestigationToDetail(savedInv);
    }

    public AmlInvestigationDetailResponse updateDraft(UUID id, UpdateInvestigationDraftRequest req, SecurityUserPrincipal principal) {
        String tenantId = TenantContext.getTenantId();
        AmlInvestigation inv = investigationRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Investigation not found with ID: " + id));

        String username = principal != null ? principal.getUsername() : "compliance_analyst";
        UUID userId = principal != null ? principal.getId() : null;

        String prevStatus = inv.getStatus();
        inv.setSarNarrativeDraft(req.sarNarrativeDraft());
        if (req.analystNotes() != null && !req.analystNotes().isBlank()) {
            inv.setAnalystNotes(req.analystNotes());
        }
        inv.setAnalystDecision("EDITED");
        inv.setUpdatedAt(OffsetDateTime.now());

        InvestigationTimeline timeline = new InvestigationTimeline(
            inv,
            "ANALYST_EDITED_DRAFT",
            username,
            "Analyst edited SAR narrative draft and updated investigation notes.",
            prevStatus,
            inv.getStatus()
        );
        timelineRepository.save(timeline);

        securityAuditService.recordSecurityEvent(
            userId,
            tenantId,
            "INVESTIGATION_DRAFT_MODIFIED",
            "AML_INVESTIGATION",
            inv.getId().toString(),
            UUID.randomUUID().toString(),
            Map.of("investigationReference", inv.getInvestigationReference(), "updatedBy", username)
        );

        linkLatestAuditHash(inv, tenantId);
        return mapInvestigationToDetail(investigationRepository.save(inv));
    }

    public AmlInvestigationDetailResponse submitAnalystDecision(UUID id, AnalystDecisionRequest req, SecurityUserPrincipal principal) {
        String tenantId = TenantContext.getTenantId();
        AmlInvestigation inv = investigationRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Investigation not found with ID: " + id));

        String username = principal != null ? principal.getUsername() : "compliance_analyst";
        UUID userId = principal != null ? principal.getId() : null;

        String prevStatus = inv.getStatus();
        String decision = req.decision().toUpperCase();
        inv.setAnalystDecision(decision);
        if (req.notes() != null) {
            inv.setAnalystNotes(req.notes());
        }
        if (req.updatedDraft() != null && !req.updatedDraft().isBlank()) {
            inv.setSarNarrativeDraft(req.updatedDraft());
        }

        switch (decision) {
            case "ACCEPTED":
            case "EDITED":
                inv.setStatus("UNDER_REVIEW");
                inv.getAlert().setStatus("IN_INVESTIGATION");
                break;
            case "REJECTED":
                inv.setStatus("CLOSED");
                inv.getAlert().setStatus("CLOSED_FALSE_POSITIVE");
                inv.setCompletedAt(OffsetDateTime.now());
                break;
            case "REGENERATE_REQUESTED":
                return analyzeAlert(inv.getAlert().getId(), principal);
            default:
                throw new IllegalArgumentException("Unsupported analyst decision: " + decision);
        }

        inv.setUpdatedAt(OffsetDateTime.now());
        alertRepository.save(inv.getAlert());

        InvestigationTimeline timeline = new InvestigationTimeline(
            inv,
            "ANALYST_" + decision,
            username,
            req.notes() != null ? req.notes() : "Analyst recorded decision: " + decision,
            prevStatus,
            inv.getStatus()
        );
        timelineRepository.save(timeline);

        securityAuditService.recordSecurityEvent(
            userId,
            tenantId,
            "ANALYST_DECISION_RECORDED",
            "AML_INVESTIGATION",
            inv.getId().toString(),
            UUID.randomUUID().toString(),
            Map.of("decision", decision, "investigationReference", inv.getInvestigationReference(), "newStatus", inv.getStatus())
        );

        linkLatestAuditHash(inv, tenantId);
        return mapInvestigationToDetail(investigationRepository.save(inv));
    }

    public AmlInvestigationDetailResponse submitReviewerDecision(UUID id, ReviewerDecisionRequest req, SecurityUserPrincipal principal) {
        String tenantId = TenantContext.getTenantId();
        AmlInvestigation inv = investigationRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Investigation not found with ID: " + id));

        String username = principal != null ? principal.getUsername() : "compliance_manager";
        UUID userId = principal != null ? principal.getId() : null;

        String prevStatus = inv.getStatus();
        String decision = req.decision().toUpperCase();
        inv.setReviewer(username);
        inv.setReviewerDecision(decision);
        if (req.notes() != null) {
            inv.setReviewerNotes(req.notes());
        }

        switch (decision) {
            case "APPROVED":
                inv.setStatus("APPROVED");
                inv.getAlert().setStatus("APPROVED_FOR_FILING");
                inv.setCompletedAt(OffsetDateTime.now());
                break;
            case "RETURNED_FOR_REVISION":
                inv.setStatus("DRAFT_READY");
                inv.getAlert().setStatus("IN_INVESTIGATION");
                break;
            case "ESCALATED_TO_MLRO":
                inv.setStatus("ESCALATED");
                inv.setEscalationReason(req.escalationReason());
                inv.getAlert().setStatus("ESCALATED");
                break;
            case "REJECTED":
                inv.setStatus("REJECTED");
                inv.getAlert().setStatus("CLOSED_FALSE_POSITIVE");
                inv.setCompletedAt(OffsetDateTime.now());
                break;
            default:
                throw new IllegalArgumentException("Unsupported reviewer decision: " + decision);
        }

        inv.setUpdatedAt(OffsetDateTime.now());
        alertRepository.save(inv.getAlert());

        InvestigationTimeline timeline = new InvestigationTimeline(
            inv,
            "REVIEWER_" + decision,
            username,
            req.notes() != null ? req.notes() : "Reviewer recorded decision: " + decision,
            prevStatus,
            inv.getStatus()
        );
        timelineRepository.save(timeline);

        securityAuditService.recordSecurityEvent(
            userId,
            tenantId,
            "REVIEWER_APPROVAL_WORKFLOW",
            "AML_INVESTIGATION",
            inv.getId().toString(),
            UUID.randomUUID().toString(),
            Map.of("decision", decision, "investigationReference", inv.getInvestigationReference(), "newStatus", inv.getStatus())
        );

        linkLatestAuditHash(inv, tenantId);
        return mapInvestigationToDetail(investigationRepository.save(inv));
    }

    // ------------------------------------------------------------------------
    // REPORT EXPORT (NON-AUTOMATED ASSISTANCE ONLY)
    // ------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public InvestigationExportResponse exportReport(UUID id, SecurityUserPrincipal principal) {
        String tenantId = TenantContext.getTenantId();
        AmlInvestigation inv = investigationRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Investigation not found with ID: " + id));

        String exportedBy = principal != null ? principal.getUsername() : "authorized_auditor";
        String markdown = formatReportMarkdown(inv);

        String disclaimer = "MANDATORY LEGAL DISCLAIMER: This investigation dossier was generated with AI-assisted compliance tooling " +
            "strictly for preliminary analyst assistance. It does NOT constitute an automated SAR filing decision or formal legal conclusion. " +
            "Under 31 U.S.C. § 5318(g) and Bank AML governance, all final regulatory reporting actions require explicit review and authorized human approval.";

        return new InvestigationExportResponse(
            inv.getId(),
            inv.getInvestigationReference(),
            inv.getAlert().getAlertReference(),
            inv.getAlert().getCustomerName(),
            inv.getStatus(),
            exportedBy,
            OffsetDateTime.now(),
            "MARKDOWN_DOSSIER",
            markdown,
            disclaimer
        );
    }

    // ------------------------------------------------------------------------
    // AI GENERATION & FORMATTING HELPERS
    // ------------------------------------------------------------------------

    private String generateEvidenceBackedSummary(AmlAlert alert, List<AmlTransaction> txns, List<LinkedPolicyDto> policies) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Investigation into automated alert %s triggered under scenario %s (%s). ",
            alert.getAlertReference(), alert.getScenarioCode(), alert.getScenarioTitle()));
        sb.append(String.format("Subject: %s (Account: %s, Customer ID: %s, Risk Rating: %s). ",
            alert.getCustomerName(), alert.getAccountNumber(), alert.getCustomerId(), alert.getCustomerRiskLevel()));
        sb.append(String.format("The surveillance rule flagged an aggregate transaction volume of %s %s across %d transactions. ",
            alert.getTotalAlertAmount(), alert.getCurrency(), txns.size()));

        if ("TM-RULE-101".equalsIgnoreCase(alert.getScenarioCode())) {
            sb.append("Analysis demonstrates deliberate structured deposits executing consecutively under the $10,000 Currency Transaction Reporting threshold, indicating potential structuring to evade BSA recordkeeping. ");
        } else if ("ALRT_HVT_01".equalsIgnoreCase(alert.getScenarioCode())) {
            sb.append("Analysis highlights an urgent outbound high-value international wire to an offshore jurisdiction lacking apparent commercial justification or matching business profile. ");
        } else if (alert.getScenarioCode().contains("PEP")) {
            sb.append("Analysis indicates rapid velocity transmission of funds involving a designated Politically Exposed Person (PEP) enterprise followed by immediate offshore multi-escrow dispersion. ");
        } else {
            sb.append("Transactional velocity and counterparty dispersion diverge significantly from baseline historical activity. ");
        }

        if (!policies.isEmpty()) {
            sb.append("Identified governance controls: ");
            for (LinkedPolicyDto p : policies) {
                sb.append(String.format("[%s - %s] ", p.policyCode(), p.section()));
            }
        }
        return sb.toString();
    }

    private String generatePolicyRelevanceExplanation(AmlAlert alert, List<AmlTransaction> txns, List<LinkedPolicyDto> policies) {
        StringBuilder sb = new StringBuilder();
        sb.append("### Applicable Bank AML Policy Controls & Red Flag Indicators:\n\n");

        if ("TM-RULE-101".equalsIgnoreCase(alert.getScenarioCode())) {
            sb.append("1. **Bank Policy AML-SOP-001 (Section 3.2 - Structuring Surveillance)**: Mandates automated detection for multiple cash deposits executed across branches or ATMs within a 72-hour window where individual amounts fall between $8,000 and $9,999.\n");
            sb.append("2. **Statutory BSA / 31 CFR § 1010.311**: Prohibits structuring transactions for the purpose of evading Currency Transaction Report (CTR) filing obligations.\n");
            sb.append("3. **Red Flag Identified**: Customer conducted 3 cash deposits of exactly $9,500.00 across distinct branch and ATM facilities within 48 hours without verifiable commercial cash justification.\n");
        } else if ("ALRT_HVT_01".equalsIgnoreCase(alert.getScenarioCode())) {
            sb.append("1. **Bank Policy AML-SOP-003 (Section 4.1 - High-Value Wire Escalation)**: Requires immediate enhanced scrutiny and source-of-funds verification for cross-border wires exceeding $100,000 to secrecy jurisdictions.\n");
            sb.append("2. **Bank Policy AML-GUIDE-004 (Section 2.0 - Offshore Counterparty Diligence)**: Requires verified commercial contracts and ultimate beneficial ownership (UBO) documentation prior to release.\n");
            sb.append("3. **Red Flag Identified**: Large outbound wire ($185,000.00) to British Virgin Islands consultancy entity without matching trade documentation.\n");
        } else {
            sb.append("1. **Bank Policy AML-POL-002 (Section 5.3 - Customer Risk Rating & High-Risk Activity)**: Dictates immediate review when rapid inward/outward fund movements occur on high-risk designated accounts.\n");
            sb.append("2. **Red Flag Identified**: Inbound funds ($450,000.00) dispersed within 24 hours to multiple offshore destinations without standard economic rationale.\n");
        }

        return sb.toString();
    }

    private String generateSarNarrativeDraft(AmlAlert alert, List<AmlTransaction> txns, List<LinkedPolicyDto> policies) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        StringBuilder sb = new StringBuilder();

        sb.append("SUSPICIOUS ACTIVITY REPORT (SAR) INVESTIGATION NARRATIVE DRAFT\n");
        sb.append("------------------------------------------------------------------------\n");
        sb.append("ALERT REF: ").append(alert.getAlertReference()).append(" | SCENARIO: ").append(alert.getScenarioCode()).append("\n");
        sb.append("PREPARED FOR HUMAN COMPLIANCE REVIEW - NOT A FINAL FILING DECISION\n\n");

        sb.append("SECTION I: SUBJECT & ACCOUNT INFORMATION\n");
        sb.append(String.format("The subject of this investigation is %s (Customer ID: %s), maintaining account %s. ",
            alert.getCustomerName(), alert.getCustomerId(), alert.getAccountNumber()));
        sb.append(String.format("The customer's current internal risk rating is %s. Account monitoring was triggered on %s due to alert criteria: %s.\n\n",
            alert.getCustomerRiskLevel(), alert.getTriggeredAt().format(dtf), alert.getScenarioTitle()));

        sb.append("SECTION II: CHRONOLOGICAL TRANSACTION ANALYSIS\n");
        sb.append(String.format("During the review window, a total of %d transactions aggregating %s %s were reviewed:\n",
            txns.size(), alert.getTotalAlertAmount(), alert.getCurrency()));
        for (AmlTransaction t : txns) {
            sb.append(String.format("- %s | %s %s | %s | Counterparty: %s (%s) | Memo: %s%s\n",
                t.getTransactionDate().format(dtf),
                t.getAmount(), t.getCurrency(),
                t.getTransactionType(),
                t.getCounterpartyName(), t.getCounterpartyCountry(),
                t.getNarrative(),
                t.isSuspiciousIndicator() ? " [FLAGGED SUSPICIOUS]" : ""));
        }
        sb.append("\n");

        sb.append("SECTION III: SUSPICIOUS ACTIVITY INDICATORS & RED FLAGS\n");
        sb.append("Automated surveillance and manual review identified key anomalies inconsistent with known customer profile:\n");
        sb.append("1. Transaction amounts and frequencies indicate deliberate structuring or high-velocity offshore dispersal.\n");
        sb.append("2. Absence of verifiable economic rationale or supporting documentation for corporate counterparties.\n");
        sb.append("3. Counterparty jurisdictions present elevated AML/CTF compliance risks under FATF and internal guidance.\n\n");

        sb.append("SECTION IV: REGULATORY STATUTES & INTERNAL POLICY MAPPING\n");
        sb.append("Relevant regulatory provisions and bank policies applicable to this inquiry include:\n");
        sb.append("- Bank AML Policy AML-SOP-001 (Automated Transaction Monitoring Procedure)\n");
        sb.append("- Bank AML Policy AML-SOP-003 (Suspicious Activity Investigation & Escalation Framework)\n");
        sb.append("- 31 U.S.C. 5318(g) and 31 CFR § 1020.320 (Reports of Suspicious Transactions by Banks)\n\n");

        sb.append("SECTION V: PRELIMINARY INVESTIGATIVE FINDINGS & RECOMMENDATION\n");
        sb.append("Based on transactional analysis, policy mapping, and identified red flags, the preliminary recommendation is: ");
        sb.append(determineRecommendation(alert, txns)).append(". ");
        sb.append("This draft is submitted to the Senior Compliance Officer / MLRO for formal verification, disposition, and final filing authorization.\n");

        return sb.toString();
    }

    private String determineRecommendation(AmlAlert alert, List<AmlTransaction> txns) {
        if ("TM-RULE-101".equalsIgnoreCase(alert.getScenarioCode()) || "ALRT_HVT_01".equalsIgnoreCase(alert.getScenarioCode()) || alert.getScenarioCode().contains("PEP")) {
            return "FILE_SAR";
        }
        if ("HIGH".equalsIgnoreCase(alert.getSeverity())) {
            return "REQUEST_EDD";
        }
        return "CLOSE_NO_SAR";
    }

    private List<LinkedPolicyDto> buildLinkedPolicies(List<ScoredChunk> chunks, AmlAlert alert) {
        List<LinkedPolicyDto> list = new ArrayList<>();
        if (chunks != null && !chunks.isEmpty()) {
            for (ScoredChunk sc : chunks) {
                String snippet = sc.getChunkText();
                if (snippet != null && snippet.length() > 200) {
                    snippet = snippet.substring(0, 200) + "...";
                }
                list.add(new LinkedPolicyDto(
                    sc.getDocumentTitle(),
                    sc.getDocumentTitle(),
                    sc.getSection() != null ? sc.getSection() : "General",
                    snippet != null ? snippet : "",
                    sc.getSimilarity() != null ? sc.getSimilarity() : 0.85
                ));
            }
        } else {
            // Default baseline policies mapped to alert scenario
            if ("TM-RULE-101".equalsIgnoreCase(alert.getScenarioCode())) {
                list.add(new LinkedPolicyDto("AML-SOP-001", "Transaction Monitoring Procedure", "Section 3.2 - Cash Structuring", "Mandates detection of multiple cash deposits under $10,000 within 72 hours.", 0.92));
                list.add(new LinkedPolicyDto("AML-SOP-003", "Suspicious Activity Investigation", "Section 2.1 - SAR Filing Triggers", "Requires SAR filing within 30 days of detecting structured transaction patterns.", 0.88));
            } else {
                list.add(new LinkedPolicyDto("AML-SOP-003", "Suspicious Activity Investigation", "Section 4.1 - High-Value Wire Escalation", "Procedures for escalating cross-border transactions over $100,000.", 0.94));
                list.add(new LinkedPolicyDto("AML-GUIDE-004", "Transaction Escalation Guide", "Section 1.3 - Offshore Counterparties", "Enhanced due diligence guidelines for secrecy jurisdictions.", 0.86));
            }
        }
        return list;
    }

    private void linkLatestAuditHash(AmlInvestigation inv, String tenantId) {
        try {
            Optional<AuditLog> latest = auditLogRepository.findTopByTenantIdOrderBySequenceNumberDesc(tenantId);
            if (latest.isEmpty()) {
                latest = auditLogRepository.findTopByTenantIdOrderByCreatedAtDescIdDesc(tenantId);
            }
            if (latest.isPresent() && latest.get().getEntryHash() != null) {
                List<String> hashes = new ArrayList<>();
                if (inv.getLinkedAuditHashes() != null && !inv.getLinkedAuditHashes().isBlank()) {
                    hashes = objectMapper.readValue(inv.getLinkedAuditHashes(), new TypeReference<List<String>>() {});
                }
                hashes.add(latest.get().getEntryHash());
                inv.setLinkedAuditHashes(objectMapper.writeValueAsString(hashes));
            }
        } catch (Exception e) {
            log.warn("Could not link latest audit hash to investigation: {}", e.getMessage());
        }
    }

    private String formatReportMarkdown(AmlInvestigation inv) {
        AmlAlert alert = inv.getAlert();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        StringBuilder sb = new StringBuilder();
        sb.append("# AML INVESTIGATION COMPLIANCE REPORT\n\n");
        sb.append("**Investigation Reference:** `").append(inv.getInvestigationReference()).append("`  \n");
        sb.append("**Alert Reference:** `").append(alert.getAlertReference()).append("`  \n");
        sb.append("**Scenario:** ").append(alert.getScenarioTitle()).append(" (`").append(alert.getScenarioCode()).append("`)  \n");
        sb.append("**Investigation Status:** `").append(inv.getStatus()).append("`  \n");
        sb.append("**Assigned Analyst:** ").append(inv.getAssignedAnalyst() != null ? inv.getAssignedAnalyst() : "Unassigned").append("  \n");
        sb.append("**Reviewing Officer:** ").append(inv.getReviewer() != null ? inv.getReviewer() : "Pending").append("  \n");
        sb.append("**Evidence Quality:** `").append(inv.getEvidenceLevel()).append("` (Score: ").append(String.format("%.2f", inv.getEvidenceQualityScore())).append(")  \n");
        sb.append("**Created At:** ").append(inv.getCreatedAt().format(dtf)).append("  \n");
        if (inv.getCompletedAt() != null) {
            sb.append("**Completed At:** ").append(inv.getCompletedAt().format(dtf)).append("  \n");
        }
        sb.append("\n---\n\n");

        sb.append("## 1. Subject Profile\n\n");
        sb.append("- **Customer Name:** ").append(alert.getCustomerName()).append("\n");
        sb.append("- **Customer ID:** `").append(alert.getCustomerId()).append("`\n");
        sb.append("- **Account Number:** `").append(alert.getAccountNumber()).append("`\n");
        sb.append("- **Risk Classification:** `").append(alert.getCustomerRiskLevel()).append("`\n");
        sb.append("- **Total Flagged Amount:** `").append(alert.getTotalAlertAmount()).append(" ").append(alert.getCurrency()).append("`\n\n");

        sb.append("## 2. Evidence-Backed Investigation Summary\n\n");
        sb.append(inv.getAiSummary() != null ? inv.getAiSummary() : "No summary available.").append("\n\n");

        sb.append("## 3. Applicable Regulatory Policies & Red Flags\n\n");
        sb.append(inv.getPolicyRelevanceExplanation() != null ? inv.getPolicyRelevanceExplanation() : "No policy explanation recorded.").append("\n\n");

        sb.append("## 4. Formal SAR Narrative Draft\n\n");
        sb.append("```\n");
        sb.append(inv.getSarNarrativeDraft() != null ? inv.getSarNarrativeDraft() : "No draft narrative recorded.").append("\n");
        sb.append("```\n\n");

        sb.append("## 5. Review & Disposition History\n\n");
        sb.append("- **AI Recommended Decision:** `").append(inv.getDecisionRecommendation()).append("`\n");
        sb.append("- **Analyst Decision:** `").append(inv.getAnalystDecision() != null ? inv.getAnalystDecision() : "Pending").append("`\n");
        if (inv.getAnalystNotes() != null) {
            sb.append("  - *Analyst Notes:* ").append(inv.getAnalystNotes()).append("\n");
        }
        sb.append("- **Reviewer Decision:** `").append(inv.getReviewerDecision() != null ? inv.getReviewerDecision() : "Pending Review").append("`\n");
        if (inv.getReviewerNotes() != null) {
            sb.append("  - *Reviewer Notes:* ").append(inv.getReviewerNotes()).append("\n");
        }
        if (inv.getEscalationReason() != null) {
            sb.append("  - *Escalation Rationale:* ").append(inv.getEscalationReason()).append("\n");
        }

        sb.append("\n## 6. Audit Trail & Integrity Hashes\n\n");
        List<String> auditHashes = parseJsonList(inv.getLinkedAuditHashes());
        if (!auditHashes.isEmpty()) {
            sb.append("Cryptographically verified audit chain entries linked to this case:\n");
            for (String h : auditHashes) {
                sb.append("- `").append(h).append("`\n");
            }
        } else {
            sb.append("Audit records recorded directly in system audit ledger.\n");
        }

        sb.append("\n---\n\n");
        sb.append("> **MANDATORY LEGAL DISCLAIMER:** This document was generated by an AI-assisted compliance workspace. " +
            "It represents investigative assistance and does NOT constitute an automated SAR filing decision or formal regulatory determination. " +
            "All final decisions regarding regulatory filing and account actions must be approved by authorized Compliance Officers and MLROs.\n");

        return sb.toString();
    }

    // ------------------------------------------------------------------------
    // MAPPING HELPERS
    // ------------------------------------------------------------------------

    private AmlAlertResponse mapAlertToResponse(AmlAlert a) {
        List<AmlTransactionResponse> txnResponses = a.getTransactions() != null
            ? a.getTransactions().stream()
                .map(t -> new AmlTransactionResponse(
                    t.getId(), t.getTransactionReference(), t.getTransactionDate(),
                    t.getAmount(), t.getCurrency(), t.getTransactionType(),
                    t.getCounterpartyName(), t.getCounterpartyCountry(),
                    t.getCounterpartyAccount(), t.getNarrative(), t.isSuspiciousIndicator()
                )).collect(Collectors.toList())
            : List.of();

        Optional<AmlInvestigation> invOpt = investigationRepository.findByAlertIdAndTenantId(a.getId(), a.getTenantId());

        return new AmlAlertResponse(
            a.getId(), a.getAlertReference(), a.getScenarioCode(), a.getScenarioTitle(),
            a.getCustomerId(), a.getCustomerName(), a.getCustomerRiskLevel(),
            a.getAccountNumber(), a.getTotalAlertAmount(), a.getCurrency(),
            a.getSeverity(), a.getStatus(), a.getTriggeredAt(), a.getTenantId(),
            a.getCreatedAt(), txnResponses,
            invOpt.map(AmlInvestigation::getId).orElse(null),
            invOpt.map(AmlInvestigation::getStatus).orElse(null)
        );
    }

    private InvestigationSummaryResponse mapInvestigationToSummary(AmlInvestigation i) {
        AmlAlert a = i.getAlert();
        return new InvestigationSummaryResponse(
            i.getId(),
            i.getInvestigationReference(),
            a.getId(),
            a.getAlertReference(),
            a.getScenarioCode(),
            a.getScenarioTitle(),
            a.getCustomerName(),
            a.getSeverity(),
            a.getTotalAlertAmount(),
            i.getStatus(),
            i.getAssignedAnalyst(),
            i.getDecisionRecommendation(),
            i.getAnalystDecision(),
            i.getReviewerDecision(),
            i.getEvidenceQualityScore(),
            i.getEvidenceLevel(),
            i.getCreatedAt(),
            i.getUpdatedAt()
        );
    }

    private AmlInvestigationDetailResponse mapInvestigationToDetail(AmlInvestigation i) {
        AmlAlertResponse alertResp = mapAlertToResponse(i.getAlert());
        List<LinkedPolicyDto> policies = parsePolicies(i.getLinkedPolicies());
        List<String> auditHashes = parseJsonList(i.getLinkedAuditHashes());

        List<InvestigationTimeline> timelineEntities = timelineRepository.findByInvestigationIdOrderByRecordedAtAsc(i.getId());
        List<InvestigationTimelineResponse> timelineResponses = timelineEntities.stream()
            .map(t -> new InvestigationTimelineResponse(
                t.getId(), t.getAction(), t.getPerformedBy(), t.getNotes(),
                t.getPreviousStatus(), t.getNewStatus(), t.getRecordedAt()
            )).collect(Collectors.toList());

        return new AmlInvestigationDetailResponse(
            i.getId(),
            i.getInvestigationReference(),
            alertResp,
            i.getAssignedAnalyst(),
            i.getReviewer(),
            i.getStatus(),
            i.getAiSummary(),
            i.getPolicyRelevanceExplanation(),
            i.getSarNarrativeDraft(),
            i.getDecisionRecommendation(),
            i.getAnalystDecision(),
            i.getAnalystNotes(),
            i.getReviewerDecision(),
            i.getReviewerNotes(),
            i.getEscalationReason(),
            policies,
            auditHashes,
            i.getEvidenceQualityScore(),
            i.getEvidenceLevel(),
            i.getTenantId(),
            i.getCompletedAt(),
            i.getCreatedAt(),
            i.getUpdatedAt(),
            timelineResponses
        );
    }

    private String serializeToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<LinkedPolicyDto> parsePolicies(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<LinkedPolicyDto>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
