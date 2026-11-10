package com.bank.aml.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InvestigationExportResponse(
    UUID investigationId,
    String investigationReference,
    String alertReference,
    String customerName,
    String status,
    String exportedBy,
    OffsetDateTime exportedAt,
    String reportFormat,
    String markdownContent,
    String legalDisclaimer
) {}
