package com.bank.aml.dto.response;

import java.time.OffsetDateTime;

public record BenchmarkRunResponse(
    String id,
    String benchmarkVersion,
    int totalInquiries,
    int passedInquiries,
    double passRatePercentage,
    double groundednessRate,
    double retrievalRecallRate,
    double citationCorrectnessRate,
    double refusalQualityRate,
    double meanLatencyMs,
    double p50LatencyMs,
    double p95LatencyMs,
    OffsetDateTime timestamp,
    String triggeredBy,
    String status
) {}
