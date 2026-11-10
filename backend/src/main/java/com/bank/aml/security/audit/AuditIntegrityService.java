package com.bank.aml.security.audit;

import com.bank.aml.entity.AuditLog;
import com.bank.aml.repository.AuditLogRepository;
import com.bank.aml.security.rag.SecurityAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AuditIntegrityService {

    private static final Logger log = LoggerFactory.getLogger(AuditIntegrityService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditIntegrityService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public record AuditIntegrityReport(
        boolean valid,
        String tenantId,
        int totalRecordsChecked,
        UUID brokenRecordId,
        String failureReason,
        String headHash,
        OffsetDateTime verifiedAt
    ) {
        public static AuditIntegrityReport success(String tenantId, int count, String headHash) {
            return new AuditIntegrityReport(true, tenantId, count, null, null, headHash, OffsetDateTime.now());
        }

        public static AuditIntegrityReport failure(String tenantId, int count, UUID brokenId, String reason) {
            return new AuditIntegrityReport(false, tenantId, count, brokenId, reason, null, OffsetDateTime.now());
        }
    }

    @Transactional(readOnly = true)
    public AuditIntegrityReport verifyTenantChain(String tenantId) {
        List<AuditLog> chain = auditLogRepository.findByTenantIdOrderBySequenceNumberAsc(tenantId);
        if (chain.isEmpty()) {
            chain = auditLogRepository.findByTenantIdOrderByCreatedAtAscIdAsc(tenantId);
        }

        if (chain.isEmpty()) {
            return AuditIntegrityReport.success(tenantId, 0, SecurityAuditService.GENESIS_HASH);
        }

        String expectedPreviousHash = SecurityAuditService.GENESIS_HASH;

        for (int i = 0; i < chain.size(); i++) {
            AuditLog logEntry = chain.get(i);

            // Skip legacy unchained records from baseline database seeding
            if (SecurityAuditService.GENESIS_HASH.equals(logEntry.getEntryHash())
                && SecurityAuditService.GENESIS_HASH.equals(logEntry.getPreviousHash())) {
                continue;
            }

            // 1. Check link to previous hash
            if (logEntry.getPreviousHash() == null || !logEntry.getPreviousHash().equalsIgnoreCase(expectedPreviousHash)) {
                String error = String.format("Broken chain link at index %d (id=%s): expected previousHash=%s but found %s",
                    i, logEntry.getId(), expectedPreviousHash, logEntry.getPreviousHash());
                log.error("AUDIT INTEGRITY VIOLATION: {}", error);
                return AuditIntegrityReport.failure(tenantId, i, logEntry.getId(), error);
            }

            // 2. Recompute the entry hash from original data fields
            String recomputedHash = SecurityAuditService.computeHash(
                logEntry.getPreviousHash(),
                logEntry.getId(),
                logEntry.getTenantId(),
                logEntry.getUserId(),
                logEntry.getAction(),
                logEntry.getResourceType(),
                logEntry.getResourceId(),
                logEntry.getMetadata()
            );

            if (logEntry.getEntryHash() == null || !logEntry.getEntryHash().equalsIgnoreCase(recomputedHash)) {
                String error = String.format("Tampered record detected at index %d (id=%s): computed hash=%s does not match stored hash=%s",
                    i, logEntry.getId(), recomputedHash, logEntry.getEntryHash());
                log.error("AUDIT INTEGRITY VIOLATION: {}", error);
                return AuditIntegrityReport.failure(tenantId, i, logEntry.getId(), error);
            }

            expectedPreviousHash = logEntry.getEntryHash();
        }

        log.info("Audit chain integrity verified successfully for tenant '{}': {} records validated, head={}",
            tenantId, chain.size(), expectedPreviousHash);
        return AuditIntegrityReport.success(tenantId, chain.size(), expectedPreviousHash);
    }
}
