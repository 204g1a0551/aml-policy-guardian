package com.bank.aml.dto.response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RetrievalComparisonResponse(
    String query,
    long totalComparisonTimeMs,
    Map<String, StrategyResultDto> strategies,
    Map<String, Double> strategyJaccardOverlap,
    String recommendation
) {
    public record StrategyResultDto(
        String strategyName,
        long latencyMs,
        int resultCount,
        Double topScore,
        List<CandidateChunkDto> chunks
    ) {}

    public record CandidateChunkDto(
        UUID chunkId,
        UUID documentId,
        String documentTitle,
        String section,
        Integer pageNumber,
        int rank,
        Double score,
        String matchSource,
        String snippet
    ) {}
}
