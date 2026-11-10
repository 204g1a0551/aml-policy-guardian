package com.bank.aml.dto.response;

public record BenchmarkComparisonResponse(
    String baselineId,
    String candidateId,
    double passRateDelta,
    double groundednessDelta,
    double recallDelta,
    double citationDelta,
    double refusalDelta,
    double p95LatencyDeltaMs,
    boolean isRegression,
    String summaryVerdict
) {}
