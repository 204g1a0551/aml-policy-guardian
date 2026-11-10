package com.bank.aml.dto.request;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RetrievalFilterDto(
    UUID documentId,
    String documentType,
    String section,
    String retrievalStrategy,
    String policyCode,
    String jurisdiction,
    Boolean onlyEffective,
    OffsetDateTime asOfDate
) {
    public RetrievalFilterDto(UUID documentId, String documentType, String section, String retrievalStrategy) {
        this(documentId, documentType, section, retrievalStrategy, null, null, true, null);
    }
}
