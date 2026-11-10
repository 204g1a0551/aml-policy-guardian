package com.bank.aml.kafka;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

public class DocumentEvent implements Serializable {

    private String eventId;
    private String eventType; // DOCUMENT_UPLOADED, INGESTION_REQUESTED, INDEXING_COMPLETED, INDEXING_FAILED, INDEXING_DLT
    private UUID jobId;
    private UUID documentId;
    private String tenantId;
    private String policyCode;
    private String filename;
    private Long fileSizeBytes;
    private int retryCount;
    private String errorMessage;
    private String correlationId;
    private OffsetDateTime timestamp;

    public DocumentEvent() {
    }

    public DocumentEvent(
        String eventType,
        UUID jobId,
        UUID documentId,
        String tenantId,
        String policyCode,
        String filename,
        Long fileSizeBytes,
        int retryCount,
        String errorMessage,
        String correlationId
    ) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.jobId = jobId;
        this.documentId = documentId;
        this.tenantId = tenantId != null ? tenantId : "default-bank";
        this.policyCode = policyCode;
        this.filename = filename;
        this.fileSizeBytes = fileSizeBytes;
        this.retryCount = retryCount;
        this.errorMessage = errorMessage;
        this.correlationId = correlationId;
        this.timestamp = OffsetDateTime.now();
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getPolicyCode() {
        return policyCode;
    }

    public void setPolicyCode(String policyCode) {
        this.policyCode = policyCode;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "DocumentEvent{" +
            "eventId='" + eventId + '\'' +
            ", eventType='" + eventType + '\'' +
            ", jobId=" + jobId +
            ", documentId=" + documentId +
            ", tenantId='" + tenantId + '\'' +
            ", policyCode='" + policyCode + '\'' +
            ", retryCount=" + retryCount +
            '}';
    }
}
