package com.bank.aml.controller;

import com.bank.aml.entity.SecurityAlert;
import com.bank.aml.security.alert.SecurityAlertService;
import com.bank.aml.security.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/security/alerts")
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class SecurityAlertController {

    private final SecurityAlertService securityAlertService;

    public SecurityAlertController(SecurityAlertService securityAlertService) {
        this.securityAlertService = securityAlertService;
    }

    @GetMapping
    public ResponseEntity<List<SecurityAlert>> getSecurityAlerts(
        @RequestParam(value = "tenantId", required = false) String tenantId
    ) {
        String activeTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : TenantContext.getTenantId();
        List<SecurityAlert> alerts = securityAlertService.getAlertsForTenant(activeTenant);
        return ResponseEntity.ok(alerts);
    }
}
