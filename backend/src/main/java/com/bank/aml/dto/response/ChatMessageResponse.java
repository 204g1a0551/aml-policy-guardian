package com.bank.aml.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ChatMessageResponse(
    UUID id,
    UUID sessionId,
    String role,
    String content,
    List<CitationResponse> citations,
    OffsetDateTime createdAt,
    Double evidenceQualityScore,
    String evidenceLevel,
    Boolean isRefusal,
    String retrievalStrategy
) {
    public ChatMessageResponse(UUID id, UUID sessionId, String role, String content, List<CitationResponse> citations, OffsetDateTime createdAt) {
        this(id, sessionId, role, content, citations, createdAt, null, null, false, null);
    }

    public ChatMessageResponse(UUID id, String role, String content, List<CitationResponse> citations, OffsetDateTime createdAt) {
        this(id, null, role, content, citations, createdAt, null, null, false, null);
    }
}
