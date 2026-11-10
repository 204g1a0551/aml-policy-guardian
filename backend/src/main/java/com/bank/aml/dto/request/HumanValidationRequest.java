package com.bank.aml.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record HumanValidationRequest(
    @NotBlank(message = "Validation status is required")
    @Pattern(regexp = "VALIDATED|REJECTED", message = "Status must be either VALIDATED or REJECTED")
    String status,

    String notes
) {}
