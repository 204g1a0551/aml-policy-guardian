package com.bank.aml.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AmlAlertResponse(
    UUID id,
    String alertReference,
    String scenarioCode,
    String scenarioTitle,
    String customerId,
    String customerName,
    String customerRiskLevel,
    String accountNumber,
    BigDecimal totalAlertAmount,
    String currency,
    String severity,
    String status,
    OffsetDateTime triggeredAt,
    String tenantId,
    OffsetDateTime createdAt,
    List<AmlTransactionResponse> transactions,
    UUID investigationId,
    String investigationStatus
) {}
