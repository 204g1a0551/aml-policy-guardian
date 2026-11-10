package com.bank.aml.service;

import com.bank.aml.dto.request.HumanValidationRequest;
import com.bank.aml.dto.response.*;
import com.bank.aml.entity.ComplianceNotification;
import com.bank.aml.entity.Document;
import com.bank.aml.entity.DocumentChunk;
import com.bank.aml.entity.PolicySectionDiff;
import com.bank.aml.exception.ResourceNotFoundException;
import com.bank.aml.repository.ComplianceNotificationRepository;
import com.bank.aml.repository.DocumentChunkRepository;
import com.bank.aml.repository.DocumentRepository;
import com.bank.aml.repository.PolicySectionDiffRepository;
import com.bank.aml.security.SecurityUserPrincipal;
import com.bank.aml.security.rag.SecurityAuditService;
import com.bank.aml.security.tenant.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RegulatoryChangeService {

    private static final Logger log = LoggerFactory.getLogger(RegulatoryChangeService.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final PolicySectionDiffRepository diffRepository;
    private final ComplianceNotificationRepository notificationRepository;
    private final SectionComparisonService sectionComparisonService;
    private final SecurityAuditService securityAuditService;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.bank.aml.cache.TenantAwareCacheService cacheService;

    public RegulatoryChangeService(
        DocumentRepository documentRepository,
        DocumentChunkRepository documentChunkRepository,
        PolicySectionDiffRepository diffRepository,
        ComplianceNotificationRepository notificationRepository,
        SectionComparisonService sectionComparisonService,
        SecurityAuditService securityAuditService,
        ObjectMapper objectMapper
    ) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.diffRepository = diffRepository;
        this.notificationRepository = notificationRepository;
        this.sectionComparisonService = sectionComparisonService;
        this.securityAuditService = securityAuditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<PolicyFamilySummaryResponse> listPolicyFamilies() {
        String tenantId = TenantContext.getTenantId();
        List<String> policyCodes = documentRepository.findDistinctPolicyCodesByTenantId(tenantId);

        List<PolicyFamilySummaryResponse> summaries = new ArrayList<>();
        for (String code : policyCodes) {
            List<Document> docs = documentRepository.findByTenantIdAndPolicyCodeOrderByCreatedAtDesc(tenantId, code);
            if (docs.isEmpty()) continue;

            Document effectiveDoc = docs.stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsEffective()))
                .findFirst()
                .orElse(docs.get(0));

            int pendingCount = (int) docs.stream()
                .filter(d -> "PENDING_APPROVAL".equalsIgnoreCase(d.getApprovalStatus()) || "DRAFT".equalsIgnoreCase(d.getApprovalStatus()))
                .count();

            summaries.add(new PolicyFamilySummaryResponse(
                code,
                effectiveDoc.getTitle(),
                effectiveDoc.getDocumentType(),
                effectiveDoc.getJurisdiction(),
                effectiveDoc.getVersion(),
                effectiveDoc.getId(),
                effectiveDoc.getEffectiveDate(),
                docs.size(),
                pendingCount
            ));
        }
        return summaries;
    }

    @Transactional(readOnly = true)
    public List<PolicyVersionDetailResponse> getPolicyVersions(String policyCode) {
        String tenantId = TenantContext.getTenantId();
        List<Document> docs = documentRepository.findByTenantIdAndPolicyCodeOrderByCreatedAtDesc(tenantId, policyCode);
        return docs.stream()
            .map(PolicyVersionDetailResponse::fromEntity)
            .collect(Collectors.toList());
    }

    @Transactional
    public PolicyComparisonReportResponse comparePolicyVersions(UUID baseDocId, UUID targetDocId) {
        String tenantId = TenantContext.getTenantId();

        Document baseDoc = documentRepository.findById(baseDocId)
            .orElseThrow(() -> new ResourceNotFoundException("Base policy document not found: " + baseDocId));
        Document targetDoc = documentRepository.findById(targetDocId)
            .orElseThrow(() -> new ResourceNotFoundException("Target policy document not found: " + targetDocId));

        if (!tenantId.equalsIgnoreCase(baseDoc.getTenantId()) || !tenantId.equalsIgnoreCase(targetDoc.getTenantId())) {
            throw new AccessDeniedException("Access denied: Policy documents belong to an isolated tenant.");
        }

        List<PolicySectionDiff> existingDiffs = diffRepository.findByTenantIdAndBaseDocumentIdAndTargetDocumentId(
            tenantId, baseDocId, targetDocId
        );

        List<PolicySectionDiff> diffs;
        if (existingDiffs.isEmpty()) {
            diffs = sectionComparisonService.comparePolicyDocuments(baseDoc, targetDoc);
            diffs = diffRepository.saveAll(diffs);
        } else {
            diffs = existingDiffs;
        }

        int additions = 0;
        int deletions = 0;
        int modifications = 0;
        int unchanged = 0;
        int pendingValidation = 0;

        List<SectionDiffResponse> diffResponses = new ArrayList<>();
        for (PolicySectionDiff diff : diffs) {
            switch (diff.getChangeType()) {
                case "ADDITION" -> additions++;
                case "DELETION" -> deletions++;
                case "MODIFIED_OBLIGATION" -> modifications++;
                case "UNCHANGED" -> unchanged++;
            }
            if ("PENDING".equalsIgnoreCase(diff.getHumanValidationStatus())) {
                pendingValidation++;
            }

            List<String> chunkIds = parseChunkIds(diff.getAffectedChunkIds());
            diffResponses.add(SectionDiffResponse.fromEntity(diff, baseDoc.getVersion(), targetDoc.getVersion(), chunkIds));
        }

        PolicyComparisonReportResponse.ComparisonMetrics metrics = new PolicyComparisonReportResponse.ComparisonMetrics(
            diffs.size(), additions, deletions, modifications, unchanged, pendingValidation
        );

        String policyCode = targetDoc.getPolicyCode() != null ? targetDoc.getPolicyCode() : baseDoc.getPolicyCode();

        return new PolicyComparisonReportResponse(
            policyCode,
            baseDoc.getId(),
            baseDoc.getVersion(),
            baseDoc.getTitle(),
            targetDoc.getId(),
            targetDoc.getVersion(),
            targetDoc.getTitle(),
            metrics,
            diffResponses
        );
    }

    @Transactional
    public SectionDiffResponse validateDiff(UUID diffId, HumanValidationRequest request, SecurityUserPrincipal user) {
        String tenantId = TenantContext.getTenantId();
        PolicySectionDiff diff = diffRepository.findById(diffId)
            .orElseThrow(() -> new ResourceNotFoundException("Policy section diff not found: " + diffId));

        if (!tenantId.equalsIgnoreCase(diff.getTenantId())) {
            throw new AccessDeniedException("Access denied: Section diff belongs to another tenant.");
        }

        diff.setHumanValidationStatus(request.status().toUpperCase());
        diff.setRequiresHumanValidation(false);
        diff.setValidatedBy(user.getId());
        diff.setValidatedAt(OffsetDateTime.now());
        diff.setValidationNotes(request.notes());

        PolicySectionDiff saved = diffRepository.save(diff);

        // Record forensic audit event
        securityAuditService.recordSecurityEvent(
            user.getId(),
            "POLICY_DIFF_HUMAN_VALIDATION",
            "POLICY_DIFF",
            diff.getId().toString(),
            UUID.randomUUID().toString(),
            Map.of(
                "policyCode", diff.getPolicyCode(),
                "sectionTitle", diff.getSectionTitle(),
                "status", request.status(),
                "notes", request.notes() != null ? request.notes() : ""
            )
        );

        Document baseDoc = documentRepository.findById(diff.getBaseDocumentId()).orElse(null);
        Document targetDoc = documentRepository.findById(diff.getTargetDocumentId()).orElse(null);

        String baseVer = baseDoc != null ? baseDoc.getVersion() : "v1.0";
        String targetVer = targetDoc != null ? targetDoc.getVersion() : "v2.0";

        return SectionDiffResponse.fromEntity(saved, baseVer, targetVer, parseChunkIds(saved.getAffectedChunkIds()));
    }

    @Transactional
    public PolicyVersionDetailResponse approvePolicyVersion(UUID documentId, SecurityUserPrincipal user) {
        String tenantId = TenantContext.getTenantId();

        Document targetDoc = documentRepository.findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Policy document not found: " + documentId));

        if (!tenantId.equalsIgnoreCase(targetDoc.getTenantId())) {
            throw new AccessDeniedException("Access denied: Document belongs to an isolated tenant.");
        }

        String policyCode = targetDoc.getPolicyCode();
        if (policyCode == null || policyCode.isBlank()) {
            policyCode = targetDoc.getFilename().replaceAll("[^a-zA-Z0-9.-]", "_");
            targetDoc.setPolicyCode(policyCode);
        }

        // 1. Mark target document as approved and effective
        targetDoc.setApprovalStatus("APPROVED");
        targetDoc.setIsEffective(true);
        targetDoc.setStatus("READY");
        targetDoc.setApprovedBy(user.getId());
        targetDoc.setApprovedAt(OffsetDateTime.now());
        Document savedTarget = documentRepository.save(targetDoc);

        // 2. Find and supersede previous effective version of this policy
        List<Document> previousVersions = documentRepository.findByTenantIdAndPolicyCodeOrderByCreatedAtDesc(tenantId, policyCode);
        for (Document prior : previousVersions) {
            if (!prior.getId().equals(targetDoc.getId()) && Boolean.TRUE.equals(prior.getIsEffective())) {
                prior.setIsEffective(false);
                prior.setStatus("SUPERSEDED");
                prior.setSupersededBy(targetDoc.getId());
                prior.setSupersededAt(OffsetDateTime.now());
                documentRepository.save(prior);

                // Update previous chunks to inactive
                List<DocumentChunk> priorChunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(prior.getId());
                for (DocumentChunk pc : priorChunks) {
                    pc.setIsEffective(false);
                }
                documentChunkRepository.saveAll(priorChunks);

                log.info("Superseded prior policy version: id={}, code={}, version={}", prior.getId(), policyCode, prior.getVersion());
            }
        }

        // 3. Activate target document chunks
        List<DocumentChunk> targetChunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(targetDoc.getId());
        for (DocumentChunk tc : targetChunks) {
            tc.setIsEffective(true);
            tc.setPolicyCode(policyCode);
            tc.setEffectiveDate(targetDoc.getEffectiveDate());
        }
        documentChunkRepository.saveAll(targetChunks);

        // 4. Create and dispatch compliance notification to analysts
        ComplianceNotification notification = new ComplianceNotification(
            tenantId,
            "ROLE_COMPLIANCE_ANALYST",
            null,
            "Policy Update Approved: " + policyCode + " (" + targetDoc.getVersion() + ")",
            String.format("Policy '%s' (%s, version %s) has been approved by %s and is now authoritative. Applicable effective date: %s.",
                targetDoc.getTitle(), policyCode, targetDoc.getVersion(), user.getUsername(), targetDoc.getEffectiveDate()),
            policyCode,
            targetDoc.getId()
        );
        notificationRepository.save(notification);

        // 5. Tamper-evident forensic audit trail
        securityAuditService.recordSecurityEvent(
            user.getId(),
            "POLICY_VERSION_APPROVED",
            "POLICY",
            policyCode,
            UUID.randomUUID().toString(),
            Map.of(
                "documentId", targetDoc.getId().toString(),
                "version", targetDoc.getVersion(),
                "policyCode", policyCode,
                "approvedBy", user.getUsername(),
                "effectiveDate", targetDoc.getEffectiveDate().toString()
            )
        );

        log.info("Policy version approved successfully: code={}, version={}, approvedBy={}",
            policyCode, targetDoc.getVersion(), user.getUsername());

        // Invalidate tenant retrieval and policy caches on policy approval
        if (cacheService != null) {
            cacheService.evictNamespace("retrieval");
            cacheService.evictNamespace("policies");
        }

        return PolicyVersionDetailResponse.fromEntity(savedTarget);
    }

    @Transactional(readOnly = true)
    public List<ComplianceNotificationResponse> getNotifications() {
        String tenantId = TenantContext.getTenantId();
        List<ComplianceNotification> list = notificationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        return list.stream().map(ComplianceNotificationResponse::fromEntity).collect(Collectors.toList());
    }

    @Transactional
    public void markNotificationRead(UUID id) {
        String tenantId = TenantContext.getTenantId();
        ComplianceNotification n = notificationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));
        if (!tenantId.equalsIgnoreCase(n.getTenantId())) {
            throw new AccessDeniedException("Access denied");
        }
        n.setIsRead(true);
        notificationRepository.save(n);
    }

    @Transactional
    public void markAllNotificationsRead() {
        String tenantId = TenantContext.getTenantId();
        List<ComplianceNotification> list = notificationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        for (ComplianceNotification n : list) {
            n.setIsRead(true);
        }
        notificationRepository.saveAll(list);
    }

    private List<String> parseChunkIds(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
