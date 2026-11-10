package com.bank.aml.dto.response;

import com.bank.aml.observability.RagObservabilityService;

import java.time.OffsetDateTime;
import java.util.List;

public record EvaluationMetricsResponse(
    RagObservabilityService.ObservabilitySnapshot performanceSnapshot,
    String systemHealth,
    boolean regressionAlertsActive,
    List<RegressionAlertResponse> activeAlerts,
    OffsetDateTime generatedAt
) {}
