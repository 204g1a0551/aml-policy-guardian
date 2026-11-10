package com.bank.aml.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InvestigationSummaryResponse(
    UUID id,
    String investigationReference,
    UUID alertId,
    String alertReference,
    String scenarioCode,
    String scenarioTitle,
    String customerName,
    String severity,
    BigDecimal totalAlertAmount,
    String status,
    String assignedAnalyst,
    String decisionRecommendation,
    String analystDecision,
    String reviewerDecision,
    Double evidenceQualityScore,
    String evidenceLevel,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
