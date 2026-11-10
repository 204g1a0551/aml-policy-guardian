package com.bank.aml.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "aml_alerts")
public class AmlAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "alert_reference", nullable = false, unique = true, length = 64)
    private String alertReference;

    @Column(name = "scenario_code", nullable = false, length = 64)
    private String scenarioCode;

    @Column(name = "scenario_title", nullable = false, length = 255)
    private String scenarioTitle;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(name = "customer_name", nullable = false, length = 255)
    private String customerName;

    @Column(name = "customer_risk_level", nullable = false, length = 32)
    private String customerRiskLevel = "MEDIUM";

    @Column(name = "account_number", nullable = false, length = 64)
    private String accountNumber;

    @Column(name = "total_alert_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAlertAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 8)
    private String currency = "USD";

    @Column(nullable = false, length = 32)
    private String severity = "MEDIUM";

    @Column(nullable = false, length = 32)
    private String status = "NEW";

    @Column(name = "triggered_at", nullable = false)
    private OffsetDateTime triggeredAt = OffsetDateTime.now();

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "alert", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("transactionDate DESC")
    private List<AmlTransaction> transactions = new ArrayList<>();

    public AmlAlert() {}

    public AmlAlert(String alertReference, String scenarioCode, String scenarioTitle,
                    String customerId, String customerName, String customerRiskLevel,
                    String accountNumber, BigDecimal totalAlertAmount, String currency,
                    String severity, String status, OffsetDateTime triggeredAt, String tenantId) {
        this.alertReference = alertReference;
        this.scenarioCode = scenarioCode;
        this.scenarioTitle = scenarioTitle;
        this.customerId = customerId;
        this.customerName = customerName;
        this.customerRiskLevel = customerRiskLevel;
        this.accountNumber = accountNumber;
        this.totalAlertAmount = totalAlertAmount;
        this.currency = currency;
        this.severity = severity;
        this.status = status;
        this.triggeredAt = triggeredAt;
        this.tenantId = tenantId;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getAlertReference() { return alertReference; }
    public void setAlertReference(String alertReference) { this.alertReference = alertReference; }

    public String getScenarioCode() { return scenarioCode; }
    public void setScenarioCode(String scenarioCode) { this.scenarioCode = scenarioCode; }

    public String getScenarioTitle() { return scenarioTitle; }
    public void setScenarioTitle(String scenarioTitle) { this.scenarioTitle = scenarioTitle; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerRiskLevel() { return customerRiskLevel; }
    public void setCustomerRiskLevel(String customerRiskLevel) { this.customerRiskLevel = customerRiskLevel; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public BigDecimal getTotalAlertAmount() { return totalAlertAmount; }
    public void setTotalAlertAmount(BigDecimal totalAlertAmount) { this.totalAlertAmount = totalAlertAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getTriggeredAt() { return triggeredAt; }
    public void setTriggeredAt(OffsetDateTime triggeredAt) { this.triggeredAt = triggeredAt; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<AmlTransaction> getTransactions() { return transactions; }
    public void setTransactions(List<AmlTransaction> transactions) { this.transactions = transactions; }

    public void addTransaction(AmlTransaction transaction) {
        transactions.add(transaction);
        transaction.setAlert(this);
    }
}
