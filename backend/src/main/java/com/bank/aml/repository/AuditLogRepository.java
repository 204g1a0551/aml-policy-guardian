package com.bank.aml.repository;

import com.bank.aml.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<AuditLog> findByActionOrderByCreatedAtDesc(String action);
    List<AuditLog> findAllByOrderByCreatedAtDesc();

    List<AuditLog> findByTenantIdOrderByCreatedAtDesc(String tenantId);
    List<AuditLog> findByTenantIdOrderBySequenceNumberAsc(String tenantId);
    List<AuditLog> findByTenantIdOrderByCreatedAtAscIdAsc(String tenantId);
    Optional<AuditLog> findTopByTenantIdOrderBySequenceNumberDesc(String tenantId);
    Optional<AuditLog> findTopByTenantIdOrderByCreatedAtDescIdDesc(String tenantId);
}
