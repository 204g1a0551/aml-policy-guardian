package com.bank.aml.security.rag;

import com.bank.aml.entity.AuditLog;
import com.bank.aml.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class SecurityAuditService {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public SecurityAuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
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
        try {
            String metadataJson = objectMapper.writeValueAsString(details);
            AuditLog auditLog = new AuditLog(userId, action, resourceType, resourceId, requestId, metadataJson);
            auditLogRepository.save(auditLog);

            // Sanitize all logged strings against CRLF log injection attacks
            String safeAction = sanitizeForLog(action);
            String safeResourceType = sanitizeForLog(resourceType);
            String safeResourceId = sanitizeForLog(resourceId);
            String safeMetadata = sanitizeForLog(metadataJson);

            log.warn("COMPLIANCE AUDIT EVENT [{}]: user={}, resource={}:{}, details={}",
                safeAction, userId, safeResourceType, safeResourceId, safeMetadata);
        } catch (Exception e) {
            log.error("Failed to persist security audit event: {}", e.getMessage(), e);
        }
    }

    private String sanitizeForLog(String input) {
        if (input == null) {
            return "null";
        }
        return input.replace('\r', '_').replace('\n', '_');
    }
}
