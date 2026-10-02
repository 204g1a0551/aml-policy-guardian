package com.bank.aml.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ChatSessionResponse(
    UUID id,
    UUID userId,
    String title,
    List<ChatMessageResponse> messages,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
