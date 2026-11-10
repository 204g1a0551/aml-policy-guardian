package com.bank.aml.controller;

import com.bank.aml.dto.request.HumanValidationRequest;
import com.bank.aml.dto.response.*;
import com.bank.aml.security.SecurityUserPrincipal;
import com.bank.aml.service.RegulatoryChangeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/policies")
public class PolicyVersionController {

    private final RegulatoryChangeService regulatoryChangeService;

    public PolicyVersionController(RegulatoryChangeService regulatoryChangeService) {
        this.regulatoryChangeService = regulatoryChangeService;
    }

    @GetMapping
    public ResponseEntity<List<PolicyFamilySummaryResponse>> listPolicyFamilies() {
        return ResponseEntity.ok(regulatoryChangeService.listPolicyFamilies());
    }

    @GetMapping("/{policyCode}/versions")
    public ResponseEntity<List<PolicyVersionDetailResponse>> getPolicyVersions(@PathVariable String policyCode) {
        return ResponseEntity.ok(regulatoryChangeService.getPolicyVersions(policyCode));
    }

    @GetMapping("/compare")
    public ResponseEntity<PolicyComparisonReportResponse> comparePolicyVersions(
        @RequestParam UUID baseDocumentId,
        @RequestParam UUID targetDocumentId
    ) {
        return ResponseEntity.ok(regulatoryChangeService.comparePolicyVersions(baseDocumentId, targetDocumentId));
    }

    @PostMapping("/diff/{diffId}/validate")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'COMPLIANCE_ANALYST')")
    public ResponseEntity<SectionDiffResponse> validateDiff(
        @PathVariable UUID diffId,
        @Valid @RequestBody HumanValidationRequest request,
        @AuthenticationPrincipal SecurityUserPrincipal user
    ) {
        return ResponseEntity.ok(regulatoryChangeService.validateDiff(diffId, request, user));
    }

    @PostMapping("/{documentId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PolicyVersionDetailResponse> approvePolicyVersion(
        @PathVariable UUID documentId,
        @AuthenticationPrincipal SecurityUserPrincipal user
    ) {
        return ResponseEntity.ok(regulatoryChangeService.approvePolicyVersion(documentId, user));
    }

    @GetMapping("/notifications")
    public ResponseEntity<List<ComplianceNotificationResponse>> getNotifications() {
        return ResponseEntity.ok(regulatoryChangeService.getNotifications());
    }

    @PutMapping("/notifications/{id}/read")
    public ResponseEntity<Void> markNotificationRead(@PathVariable UUID id) {
        regulatoryChangeService.markNotificationRead(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/notifications/read-all")
    public ResponseEntity<Void> markAllNotificationsRead() {
        regulatoryChangeService.markAllNotificationsRead();
        return ResponseEntity.noContent().build();
    }
}
