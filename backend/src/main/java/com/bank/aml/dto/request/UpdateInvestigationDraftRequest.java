package com.bank.aml.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateInvestigationDraftRequest(
    @NotBlank(message = "SAR narrative draft cannot be empty")
    String sarNarrativeDraft,

    String analystNotes
) {}
