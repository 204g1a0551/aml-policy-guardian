package com.bank.aml.dto.response;

import com.bank.aml.entity.ComplianceNotification;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ComplianceNotificationResponse(
    UUID id,
    String title,
    String message,
    String policyCode,
    UUID documentId,
    String recipientRole,
    Boolean isRead,
    OffsetDateTime createdAt
) {
    public static ComplianceNotificationResponse fromEntity(ComplianceNotification n) {
        return new ComplianceNotificationResponse(
            n.getId(),
            n.getTitle(),
            n.getMessage(),
            n.getPolicyCode(),
            n.getDocumentId(),
            n.getRecipientRole(),
            n.getIsRead(),
            n.getCreatedAt()
        );
    }
}
