package com.bank.aml.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ChatMessageResponse(
    UUID id,
    String role,
    String content,
    List<CitationResponse> citations,
    OffsetDateTime createdAt
) {}
