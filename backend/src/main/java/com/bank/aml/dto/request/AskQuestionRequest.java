package com.bank.aml.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskQuestionRequest(
    @NotBlank(message = "Message inquiry must not be blank")
    @Size(max = 2000, message = "Inquiry must not exceed 2000 characters")
    String message
) {}
