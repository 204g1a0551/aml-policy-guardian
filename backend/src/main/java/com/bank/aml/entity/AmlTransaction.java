package com.bank.aml.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "aml_transactions")
public class AmlTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alert_id", nullable = false)
    private AmlAlert alert;

    @Column(name = "transaction_reference", nullable = false, length = 64)
    private String transactionReference;

    @Column(name = "transaction_date", nullable = false)
    private OffsetDateTime transactionDate;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 8)
    private String currency = "USD";

    @Column(name = "transaction_type", nullable = false, length = 64)
    private String transactionType;

    @Column(name = "counterparty_name", nullable = false, length = 255)
    private String counterpartyName;

    @Column(name = "counterparty_country", nullable = false, length = 64)
    private String counterpartyCountry = "USA";

    @Column(name = "counterparty_account", length = 64)
    private String counterpartyAccount;

    @Column(columnDefinition = "TEXT")
    private String narrative;

    @Column(name = "is_suspicious_indicator")
    private boolean suspiciousIndicator = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public AmlTransaction() {}

    public AmlTransaction(AmlAlert alert, String transactionReference, OffsetDateTime transactionDate,
                          BigDecimal amount, String currency, String transactionType,
                          String counterpartyName, String counterpartyCountry, String counterpartyAccount,
                          String narrative, boolean suspiciousIndicator) {
        this.alert = alert;
        this.transactionReference = transactionReference;
        this.transactionDate = transactionDate;
        this.amount = amount;
        this.currency = currency;
        this.transactionType = transactionType;
        this.counterpartyName = counterpartyName;
        this.counterpartyCountry = counterpartyCountry;
        this.counterpartyAccount = counterpartyAccount;
        this.narrative = narrative;
        this.suspiciousIndicator = suspiciousIndicator;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public AmlAlert getAlert() { return alert; }
    public void setAlert(AmlAlert alert) { this.alert = alert; }

    public String getTransactionReference() { return transactionReference; }
    public void setTransactionReference(String transactionReference) { this.transactionReference = transactionReference; }

    public OffsetDateTime getTransactionDate() { return transactionDate; }
    public void setTransactionDate(OffsetDateTime transactionDate) { this.transactionDate = transactionDate; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public String getCounterpartyName() { return counterpartyName; }
    public void setCounterpartyName(String counterpartyName) { this.counterpartyName = counterpartyName; }

    public String getCounterpartyCountry() { return counterpartyCountry; }
    public void setCounterpartyCountry(String counterpartyCountry) { this.counterpartyCountry = counterpartyCountry; }

    public String getCounterpartyAccount() { return counterpartyAccount; }
    public void setCounterpartyAccount(String counterpartyAccount) { this.counterpartyAccount = counterpartyAccount; }

    public String getNarrative() { return narrative; }
    public void setNarrative(String narrative) { this.narrative = narrative; }

    public boolean isSuspiciousIndicator() { return suspiciousIndicator; }
    public void setSuspiciousIndicator(boolean suspiciousIndicator) { this.suspiciousIndicator = suspiciousIndicator; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
