package com.bank.aml.controller;

import com.bank.aml.dto.response.AuditLogResponse;
import com.bank.aml.entity.AuditLog;
import com.bank.aml.entity.User;
import com.bank.aml.repository.AuditLogRepository;
import com.bank.aml.repository.UserRepository;
import com.bank.aml.security.audit.AuditIntegrityService;
import com.bank.aml.security.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/audit")
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class AuditController {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final AuditIntegrityService auditIntegrityService;

    public AuditController(
        AuditLogRepository auditLogRepository,
        UserRepository userRepository,
        AuditIntegrityService auditIntegrityService
    ) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.auditIntegrityService = auditIntegrityService;
    }

    @GetMapping
    public ResponseEntity<List<AuditLogResponse>> getAuditLogs(
        @RequestParam(value = "tenantId", required = false) String tenantId
    ) {
        String activeTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : TenantContext.getTenantId();
        List<AuditLog> logs = auditLogRepository.findByTenantIdOrderByCreatedAtDesc(activeTenant);

        Map<UUID, String> userNames = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));

        List<AuditLogResponse> response = logs.stream()
                .map(log -> new AuditLogResponse(
                        log.getId(),
                        log.getUserId(),
                        log.getUserId() != null ? userNames.getOrDefault(log.getUserId(), log.getUserId().toString()) : "SYSTEM",
                        log.getAction(),
                        log.getResourceType(),
                        log.getResourceId(),
                        log.getRequestId(),
                        log.getMetadata(),
                        log.getCreatedAt()
                ))
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/verify")
    public ResponseEntity<AuditIntegrityService.AuditIntegrityReport> verifyAuditChain(
        @RequestParam(value = "tenantId", required = false) String tenantId
    ) {
        String activeTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : TenantContext.getTenantId();
        AuditIntegrityService.AuditIntegrityReport report = auditIntegrityService.verifyTenantChain(activeTenant);
        return ResponseEntity.ok(report);
    }
}
