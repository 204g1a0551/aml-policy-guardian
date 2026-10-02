package com.bank.aml.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditLogResponse(
    UUID id,
    UUID userId,
    String username,
    String action,
    String resourceType,
    String resourceId,
    String requestId,
    String metadata,
    OffsetDateTime createdAt
) {}
