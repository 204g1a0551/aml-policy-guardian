package com.bank.aml.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReviewerDecisionRequest(
    @NotBlank(message = "Reviewer decision is required")
    @Pattern(regexp = "^(APPROVED|RETURNED_FOR_REVISION|ESCALATED_TO_MLRO|REJECTED)$",
             message = "Decision must be one of APPROVED, RETURNED_FOR_REVISION, ESCALATED_TO_MLRO, REJECTED")
    String decision,

    String notes,
    String escalationReason
) {}
