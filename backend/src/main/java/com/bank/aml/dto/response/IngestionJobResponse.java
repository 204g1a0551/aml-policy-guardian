package com.bank.aml.dto.response;

import com.bank.aml.entity.IngestionJob;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IngestionJobResponse(
    UUID id,
    UUID documentId,
    String tenantId,
    String jobType,
    String status,
    String stage,
    int progressPct,
    int retryCount,
    int maxRetries,
    String errorMessage,
    String correlationId,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime completedAt
) {
    public static IngestionJobResponse fromEntity(IngestionJob job) {
        if (job == null) return null;
        return new IngestionJobResponse(
            job.getId(),
            job.getDocumentId(),
            job.getTenantId(),
            job.getJobType(),
            job.getStatus(),
            job.getStage(),
            job.getProgressPct(),
            job.getRetryCount(),
            job.getMaxRetries(),
            job.getErrorMessage(),
            job.getCorrelationId(),
            job.getCreatedAt(),
            job.getUpdatedAt(),
            job.getCompletedAt()
        );
    }
}
