package com.bank.aml.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InvestigationTimelineResponse(
    UUID id,
    String action,
    String performedBy,
    String notes,
    String previousStatus,
    String newStatus,
    OffsetDateTime recordedAt
) {}
