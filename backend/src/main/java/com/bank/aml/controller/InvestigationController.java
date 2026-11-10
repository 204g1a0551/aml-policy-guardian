package com.bank.aml.controller;

import com.bank.aml.dto.request.AnalystDecisionRequest;
import com.bank.aml.dto.request.CreateAlertRequest;
import com.bank.aml.dto.request.ReviewerDecisionRequest;
import com.bank.aml.dto.request.UpdateInvestigationDraftRequest;
import com.bank.aml.dto.response.*;
import com.bank.aml.security.SecurityUserPrincipal;
import com.bank.aml.service.InvestigationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/investigations")
public class InvestigationController {

    private final InvestigationService investigationService;

    public InvestigationController(InvestigationService investigationService) {
        this.investigationService = investigationService;
    }

    // ------------------------------------------------------------------------
    // ALERT ENDPOINTS
    // ------------------------------------------------------------------------

    @GetMapping("/alerts")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'COMPLIANCE_ANALYST', 'AUDITOR')")
    public ResponseEntity<Page<AmlAlertResponse>> listAlerts(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String severity,
        @RequestParam(required = false) String scenarioCode,
        @RequestParam(required = false) String query,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("triggeredAt").descending());
        return ResponseEntity.ok(investigationService.searchAlerts(status, severity, scenarioCode, query, pageable));
    }

    @GetMapping("/alerts/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'COMPLIANCE_ANALYST', 'AUDITOR')")
    public ResponseEntity<AmlAlertResponse> getAlertDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(investigationService.getAlertDetail(id));
    }

    @PostMapping("/alerts/generate")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'COMPLIANCE_ANALYST')")
    public ResponseEntity<AmlAlertResponse> generateSyntheticAlert(
        @Valid @RequestBody CreateAlertRequest request,
        @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(investigationService.generateSyntheticAlert(request, principal));
    }

    // ------------------------------------------------------------------------
    // INVESTIGATION WORKFLOW ENDPOINTS
    // ------------------------------------------------------------------------

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'COMPLIANCE_ANALYST', 'AUDITOR')")
    public ResponseEntity<Page<InvestigationSummaryResponse>> listInvestigations(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String assignedAnalyst,
        @RequestParam(required = false) String query,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(investigationService.searchInvestigations(status, assignedAnalyst, query, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'COMPLIANCE_ANALYST', 'AUDITOR')")
    public ResponseEntity<AmlInvestigationDetailResponse> getInvestigationDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(investigationService.getInvestigationDetail(id));
    }

    @PostMapping("/alerts/{alertId}/analyze")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'COMPLIANCE_ANALYST')")
    public ResponseEntity<AmlInvestigationDetailResponse> analyzeAlert(
        @PathVariable UUID alertId,
        @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        return ResponseEntity.ok(investigationService.analyzeAlert(alertId, principal));
    }

    @PutMapping("/{id}/draft")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'COMPLIANCE_ANALYST')")
    public ResponseEntity<AmlInvestigationDetailResponse> updateDraft(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateInvestigationDraftRequest request,
        @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        return ResponseEntity.ok(investigationService.updateDraft(id, request, principal));
    }

    @PostMapping("/{id}/analyst-decision")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'COMPLIANCE_ANALYST')")
    public ResponseEntity<AmlInvestigationDetailResponse> submitAnalystDecision(
        @PathVariable UUID id,
        @Valid @RequestBody AnalystDecisionRequest request,
        @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        return ResponseEntity.ok(investigationService.submitAnalystDecision(id, request, principal));
    }

    @PostMapping("/{id}/reviewer-decision")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN')")
    public ResponseEntity<AmlInvestigationDetailResponse> submitReviewerDecision(
        @PathVariable UUID id,
        @Valid @RequestBody ReviewerDecisionRequest request,
        @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        return ResponseEntity.ok(investigationService.submitReviewerDecision(id, request, principal));
    }

    @GetMapping("/{id}/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'COMPLIANCE_ANALYST', 'AUDITOR')")
    public ResponseEntity<InvestigationExportResponse> exportReport(
        @PathVariable UUID id,
        @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        return ResponseEntity.ok(investigationService.exportReport(id, principal));
    }
}
