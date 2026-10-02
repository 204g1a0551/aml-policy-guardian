package com.bank.aml.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentUploadRequest(
    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    String title,

    @NotBlank(message = "Document type is required (e.g. POLICY, PROCEDURE, REGULATION)")
    @Size(max = 50, message = "Document type must not exceed 50 characters")
    String documentType,

    @NotBlank(message = "Version is required (e.g. v1.0)")
    @Size(max = 50, message = "Version must not exceed 50 characters")
    String version,

    @Size(max = 255, message = "Source must not exceed 255 characters")
    String source
) {}
