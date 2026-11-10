package com.bank.aml.security.alert;

import com.bank.aml.entity.SecurityAlert;
import com.bank.aml.repository.SecurityAlertRepository;
import com.bank.aml.security.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SecurityAlertService {

    private static final Logger log = LoggerFactory.getLogger(SecurityAlertService.class);

    private final SecurityAlertRepository alertRepository;
    private final ObjectMapper objectMapper;

    public SecurityAlertService(SecurityAlertRepository alertRepository, ObjectMapper objectMapper) {
        this.alertRepository = alertRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public SecurityAlert triggerAlert(
        String eventType,
        String severity,
        UUID userId,
        String clientIp,
        Map<String, Object> details
    ) {
        return triggerAlert(TenantContext.getTenantId(), eventType, severity, userId, clientIp, details);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public SecurityAlert triggerAlert(
        String tenantId,
        String eventType,
        String severity,
        UUID userId,
        String clientIp,
        Map<String, Object> details
    ) {
        try {
            String activeTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : TenantContext.getTenantId();
            String detailsJson = objectMapper.writeValueAsString(details != null ? details : Map.of());

            SecurityAlert alert = new SecurityAlert(activeTenant, eventType, severity, userId, clientIp, detailsJson);
            SecurityAlert saved = alertRepository.save(alert);

            log.warn("🚨 SECURITY THREAT ALERT [{} | {}]: tenant={}, user={}, ip={}, details={}",
                severity, eventType, activeTenant, userId, clientIp, detailsJson.replaceAll("[\r\n]", "_"));

            return saved;
        } catch (Exception e) {
            log.error("Failed to record security alert: {}", e.getMessage(), e);
            return null;
        }
    }

    @Transactional(readOnly = true)
    public List<SecurityAlert> getAlertsForTenant(String tenantId) {
        String activeTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : TenantContext.getTenantId();
        return alertRepository.findByTenantIdOrderByCreatedAtDesc(activeTenant);
    }
}
