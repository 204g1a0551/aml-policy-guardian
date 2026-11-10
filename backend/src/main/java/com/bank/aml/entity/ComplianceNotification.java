package com.bank.aml.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "compliance_notifications")
public class ComplianceNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId = "default-bank";

    @Column(name = "recipient_role", nullable = false, length = 50)
    private String recipientRole = "ROLE_COMPLIANCE_ANALYST";

    @Column(name = "recipient_user_id")
    private UUID recipientUserId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "policy_code", length = 100)
    private String policyCode;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public ComplianceNotification() {}

    public ComplianceNotification(
        String tenantId,
        String recipientRole,
        UUID recipientUserId,
        String title,
        String message,
        String policyCode,
        UUID documentId
    ) {
        this.tenantId = tenantId != null ? tenantId : "default-bank";
        this.recipientRole = recipientRole != null ? recipientRole : "ROLE_COMPLIANCE_ANALYST";
        this.recipientUserId = recipientUserId;
        this.title = title;
        this.message = message;
        this.policyCode = policyCode;
        this.documentId = documentId;
        this.isRead = false;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getRecipientRole() { return recipientRole; }
    public void setRecipientRole(String recipientRole) { this.recipientRole = recipientRole; }

    public UUID getRecipientUserId() { return recipientUserId; }
    public void setRecipientUserId(UUID recipientUserId) { this.recipientUserId = recipientUserId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getPolicyCode() { return policyCode; }
    public void setPolicyCode(String policyCode) { this.policyCode = policyCode; }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }

    public Boolean getIsRead() { return isRead; }
    public void setIsRead(Boolean isRead) { this.isRead = isRead; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
