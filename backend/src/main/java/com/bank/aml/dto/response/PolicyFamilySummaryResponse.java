package com.bank.aml.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PolicyFamilySummaryResponse(
    String policyCode,
    String title,
    String documentType,
    String jurisdiction,
    String effectiveVersion,
    UUID effectiveDocumentId,
    OffsetDateTime effectiveDate,
    int totalVersions,
    int pendingApprovalCount
) {}
