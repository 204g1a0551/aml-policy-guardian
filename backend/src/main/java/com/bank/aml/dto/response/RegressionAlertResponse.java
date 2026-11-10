package com.bank.aml.dto.response;

import java.time.OffsetDateTime;

public record RegressionAlertResponse(
    String id,
    String alertType,
    String severity,
    String metricName,
    double currentValue,
    double threshold,
    String message,
    OffsetDateTime triggeredAt
) {}
