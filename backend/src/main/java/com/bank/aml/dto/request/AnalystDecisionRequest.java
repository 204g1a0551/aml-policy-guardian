package com.bank.aml.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AnalystDecisionRequest(
    @NotBlank(message = "Decision is required")
    @Pattern(regexp = "^(ACCEPTED|EDITED|REJECTED|REGENERATE_REQUESTED)$",
             message = "Decision must be one of ACCEPTED, EDITED, REJECTED, REGENERATE_REQUESTED")
    String decision,

    String updatedDraft,
    String notes
) {}
