package com.bank.aml.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ChatStreamEvent(
    String type,
    String correlationId,
    UUID sessionId,
    UUID messageId,
    String token,
    List<CitationResponse> citations,
    String error,
    OffsetDateTime timestamp
) {
    public static ChatStreamEvent start(String correlationId, UUID sessionId) {
        return new ChatStreamEvent("START", correlationId, sessionId, null, null, null, null, OffsetDateTime.now());
    }

    public static ChatStreamEvent citations(String correlationId, UUID sessionId, List<CitationResponse> citations) {
        return new ChatStreamEvent("CITATIONS", correlationId, sessionId, null, null, citations, null, OffsetDateTime.now());
    }

    public static ChatStreamEvent token(String correlationId, UUID sessionId, String token) {
        return new ChatStreamEvent("TOKEN", correlationId, sessionId, null, token, null, null, OffsetDateTime.now());
    }

    public static ChatStreamEvent complete(String correlationId, UUID sessionId, UUID messageId) {
        return new ChatStreamEvent("COMPLETE", correlationId, sessionId, messageId, null, null, null, OffsetDateTime.now());
    }

    public static ChatStreamEvent error(String correlationId, UUID sessionId, String error) {
        return new ChatStreamEvent("ERROR", correlationId, sessionId, null, null, null, error, OffsetDateTime.now());
    }
}
