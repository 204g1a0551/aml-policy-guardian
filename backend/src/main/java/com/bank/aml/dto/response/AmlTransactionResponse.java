package com.bank.aml.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AmlTransactionResponse(
    UUID id,
    String transactionReference,
    OffsetDateTime transactionDate,
    BigDecimal amount,
    String currency,
    String transactionType,
    String counterpartyName,
    String counterpartyCountry,
    String counterpartyAccount,
    String narrative,
    boolean suspiciousIndicator
) {}
