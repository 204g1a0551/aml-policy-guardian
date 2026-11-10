package com.bank.aml.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateAlertRequest(
    @NotBlank(message = "Scenario code is required")
    String scenarioCode,

    @NotBlank(message = "Scenario title is required")
    String scenarioTitle,

    @NotBlank(message = "Customer ID is required")
    String customerId,

    @NotBlank(message = "Customer name is required")
    String customerName,

    String customerRiskLevel,

    @NotBlank(message = "Account number is required")
    String accountNumber,

    @NotNull(message = "Total alert amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    BigDecimal totalAlertAmount,

    String currency,
    String severity
) {}
