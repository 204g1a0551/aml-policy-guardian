package com.bank.aml.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record StreamQuestionRequest(
    UUID sessionId,
    @JsonAlias({"query", "content", "prompt", "inquiry"})
    @NotBlank(message = "Message inquiry must not be blank")
    @Size(max = 2000, message = "Inquiry must not exceed 2000 characters")
    String message
) {}
