package com.bank.aml.repository;

import com.bank.aml.entity.ComplianceNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ComplianceNotificationRepository extends JpaRepository<ComplianceNotification, UUID> {

    List<ComplianceNotification> findByTenantIdAndRecipientRoleAndIsReadFalseOrderByCreatedAtDesc(String tenantId, String recipientRole);

    List<ComplianceNotification> findByTenantIdAndRecipientUserIdAndIsReadFalseOrderByCreatedAtDesc(String tenantId, UUID recipientUserId);

    List<ComplianceNotification> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    long countByTenantIdAndIsReadFalse(String tenantId);
}
