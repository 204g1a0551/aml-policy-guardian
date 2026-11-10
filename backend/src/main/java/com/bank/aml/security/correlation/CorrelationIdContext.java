package com.bank.aml.security.correlation;

import java.util.UUID;

/**
 * ThreadLocal context for structured request correlation IDs across
 * REST APIs, RAG retrieval pipelines, LLM invocations, and SSE streams.
 */
public final class CorrelationIdContext {

    private static final ThreadLocal<String> CURRENT_CORRELATION_ID = new ThreadLocal<>();

    private CorrelationIdContext() {}

    public static String getCorrelationId() {
        String id = CURRENT_CORRELATION_ID.get();
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
            CURRENT_CORRELATION_ID.set(id);
        }
        return id;
    }

    public static void setCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            CURRENT_CORRELATION_ID.set(correlationId);
        } else {
            CURRENT_CORRELATION_ID.set(UUID.randomUUID().toString());
        }
    }

    public static void clear() {
        CURRENT_CORRELATION_ID.remove();
    }
}
