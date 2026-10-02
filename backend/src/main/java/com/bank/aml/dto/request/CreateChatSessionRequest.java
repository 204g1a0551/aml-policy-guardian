package com.bank.aml.dto.request;

import jakarta.validation.constraints.Size;

public record CreateChatSessionRequest(
    @Size(max = 255, message = "Session title must not exceed 255 characters")
    String title
) {}
