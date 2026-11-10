package com.bank.aml.unit;

import com.bank.aml.entity.AuditLog;
import com.bank.aml.repository.AuditLogRepository;
import com.bank.aml.security.audit.AuditIntegrityService;
import com.bank.aml.security.rag.SecurityAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuditIntegrityUnitTest {

    private AuditLogRepository auditLogRepository;
    private AuditIntegrityService auditIntegrityService;

    @BeforeEach
    void setUp() {
        auditLogRepository = mock(AuditLogRepository.class);
        auditIntegrityService = new AuditIntegrityService(auditLogRepository);
    }

    @Test
    @DisplayName("Audit Chain: Valid chain passes integrity verification with head hash")
    void testValidAuditChainVerification() {
        String tenantId = "test-bank";
        List<AuditLog> chain = new ArrayList<>();

        String prevHash = SecurityAuditService.GENESIS_HASH;
        UUID user = UUID.randomUUID();

        for (int i = 0; i < 3; i++) {
            UUID id = UUID.randomUUID();
            String action = "ACTION_" + i;
            String meta = "{\"index\":" + i + "}";
            String entryHash = SecurityAuditService.computeHash(prevHash, id, tenantId, user, action, "DOC", "doc-" + i, meta);

            AuditLog log = new AuditLog(user, tenantId, action, "DOC", "doc-" + i, "req-" + i, meta, prevHash, entryHash);
            log.setId(id);
            chain.add(log);
            prevHash = entryHash;
        }

        when(auditLogRepository.findByTenantIdOrderByCreatedAtAscIdAsc(tenantId)).thenReturn(chain);

        AuditIntegrityService.AuditIntegrityReport report = auditIntegrityService.verifyTenantChain(tenantId);

        assertThat(report.valid()).isTrue();
        assertThat(report.totalRecordsChecked()).isEqualTo(3);
        assertThat(report.headHash()).isEqualTo(prevHash);
        assertThat(report.brokenRecordId()).isNull();
    }

    @Test
    @DisplayName("Audit Chain: Detects tampered action or metadata within an existing record")
    void testTamperedRecordDetected() {
        String tenantId = "test-bank";
        List<AuditLog> chain = new ArrayList<>();

        String prevHash = SecurityAuditService.GENESIS_HASH;
        UUID user = UUID.randomUUID();

        for (int i = 0; i < 3; i++) {
            UUID id = UUID.randomUUID();
            String action = "ACTION_" + i;
            String meta = "{\"index\":" + i + "}";
            String entryHash = SecurityAuditService.computeHash(prevHash, id, tenantId, user, action, "DOC", "doc-" + i, meta);

            AuditLog log = new AuditLog(user, tenantId, action, "DOC", "doc-" + i, "req-" + i, meta, prevHash, entryHash);
            log.setId(id);
            chain.add(log);
            prevHash = entryHash;
        }

        // Adversary secretly alters record #1 metadata
        chain.get(1).setMetadata("{\"tampered\": true}");

        when(auditLogRepository.findByTenantIdOrderByCreatedAtAscIdAsc(tenantId)).thenReturn(chain);

        AuditIntegrityService.AuditIntegrityReport report = auditIntegrityService.verifyTenantChain(tenantId);

        assertThat(report.valid()).isFalse();
        assertThat(report.totalRecordsChecked()).isEqualTo(1); // Fails at index 1
        assertThat(report.brokenRecordId()).isEqualTo(chain.get(1).getId());
        assertThat(report.failureReason()).contains("Tampered record detected");
    }

    @Test
    @DisplayName("Audit Chain: Detects deleted intermediate record causing chain link breakage")
    void testDeletedRecordDetected() {
        String tenantId = "test-bank";
        List<AuditLog> chain = new ArrayList<>();

        String prevHash = SecurityAuditService.GENESIS_HASH;
        UUID user = UUID.randomUUID();

        for (int i = 0; i < 4; i++) {
            UUID id = UUID.randomUUID();
            String action = "ACTION_" + i;
            String meta = "{\"index\":" + i + "}";
            String entryHash = SecurityAuditService.computeHash(prevHash, id, tenantId, user, action, "DOC", "doc-" + i, meta);

            AuditLog log = new AuditLog(user, tenantId, action, "DOC", "doc-" + i, "req-" + i, meta, prevHash, entryHash);
            log.setId(id);
            chain.add(log);
            prevHash = entryHash;
        }

        // Adversary deletes record #1
        chain.remove(1);

        when(auditLogRepository.findByTenantIdOrderByCreatedAtAscIdAsc(tenantId)).thenReturn(chain);

        AuditIntegrityService.AuditIntegrityReport report = auditIntegrityService.verifyTenantChain(tenantId);

        assertThat(report.valid()).isFalse();
        assertThat(report.failureReason()).contains("Broken chain link");
    }
}
