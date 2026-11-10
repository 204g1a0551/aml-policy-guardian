package com.bank.aml.security.rag;

import com.bank.aml.entity.AuditLog;
import com.bank.aml.repository.AuditLogRepository;
import com.bank.aml.repository.UserRepository;
import com.bank.aml.security.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class SecurityAuditService {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditService.class);
    public static final String GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    @Autowired
    public SecurityAuditService(
        AuditLogRepository auditLogRepository,
        ObjectMapper objectMapper,
        UserRepository userRepository
    ) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
    }

    public SecurityAuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this(auditLogRepository, objectMapper, null);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void recordSecurityEvent(
        UUID userId,
        String action,
        String resourceType,
        String resourceId,
        String requestId,
        Map<String, Object> details
    ) {
        recordSecurityEvent(userId, TenantContext.getTenantId(), action, resourceType, resourceId, requestId, details);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public synchronized void recordSecurityEvent(
        UUID userId,
        String tenantId,
        String action,
        String resourceType,
        String resourceId,
        String requestId,
        Map<String, Object> details
    ) {
        try {
            String activeTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : TenantContext.getTenantId();
            String metadataJson = objectMapper.writeValueAsString(details != null ? details : Map.of());

            // Check foreign key constraint safely
            UUID safeUserId = (userId != null && (userRepository == null || userRepository.existsById(userId)))
                ? userId
                : null;

            // 1. Fetch previous hash in the tenant chain
            Optional<AuditLog> latest = auditLogRepository.findTopByTenantIdOrderBySequenceNumberDesc(activeTenant);
            if (latest.isEmpty()) {
                latest = auditLogRepository.findTopByTenantIdOrderByCreatedAtDescIdDesc(activeTenant);
            }
            String previousHash = latest.map(AuditLog::getEntryHash).filter(h -> h != null && !h.isBlank()).orElse(GENESIS_HASH);

            // 2. Pre-generate ID to compute deterministic tamper-evident hash
            UUID id = UUID.randomUUID();
            String entryHash = computeHash(previousHash, id, activeTenant, safeUserId, action, resourceType, resourceId, metadataJson);

            AuditLog auditLog = new AuditLog(safeUserId, activeTenant, action, resourceType, resourceId, requestId, metadataJson, previousHash, entryHash);
            auditLog.setId(id);
            auditLogRepository.save(auditLog);

            // Sanitize all logged strings against CRLF log injection attacks
            String safeAction = sanitizeForLog(action);
            String safeResourceType = sanitizeForLog(resourceType);
            String safeResourceId = sanitizeForLog(resourceId);
            String safeMetadata = sanitizeForLog(metadataJson);

            log.warn("COMPLIANCE AUDIT EVENT [{}]: tenant={}, user={}, resource={}:{}, hash={}, details={}",
                safeAction, activeTenant, safeUserId, safeResourceType, safeResourceId, entryHash, safeMetadata);
        } catch (Exception e) {
            log.error("Failed to persist security audit event: {}", e.getMessage(), e);
        }
    }

    private static final ObjectMapper CANONICAL_MAPPER = new ObjectMapper();

    public static String canonicalizeJson(String json) {
        if (json == null || json.isBlank()) {
            return "{}";
        }
        try {
            var tree = CANONICAL_MAPPER.readTree(json);
            return CANONICAL_MAPPER.writeValueAsString(tree);
        } catch (Exception e) {
            return json.trim();
        }
    }

    public static String computeHash(
        String previousHash,
        UUID id,
        String tenantId,
        UUID userId,
        String action,
        String resourceType,
        String resourceId,
        String metadata
    ) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String canonicalMetadata = canonicalizeJson(metadata);
            String payload = (previousHash != null ? previousHash : GENESIS_HASH)
                + "|" + (id != null ? id.toString() : "")
                + "|" + (tenantId != null ? tenantId : "")
                + "|" + (userId != null ? userId.toString() : "")
                + "|" + (action != null ? action : "")
                + "|" + (resourceType != null ? resourceType : "")
                + "|" + (resourceId != null ? resourceId : "")
                + "|" + canonicalMetadata;

            byte[] digest = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    private String sanitizeForLog(String input) {
        if (input == null) {
            return "null";
        }
        return input.replace('\r', '_').replace('\n', '_');
    }
}
