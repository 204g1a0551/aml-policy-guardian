package com.bank.aml.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "policy_section_diffs")
public class PolicySectionDiff {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId = "default-bank";

    @Column(name = "policy_code", nullable = false, length = 100)
    private String policyCode;

    @Column(name = "base_document_id", nullable = false)
    private UUID baseDocumentId;

    @Column(name = "target_document_id", nullable = false)
    private UUID targetDocumentId;

    @Column(name = "section_title", nullable = false)
    private String sectionTitle;

    @Column(name = "change_type", nullable = false, length = 50)
    private String changeType; // ADDITION, DELETION, MODIFIED_OBLIGATION, UNCHANGED

    @Column(name = "base_content", columnDefinition = "TEXT")
    private String baseContent;

    @Column(name = "target_content", columnDefinition = "TEXT")
    private String targetContent;

    @Column(name = "regulatory_impact_summary", columnDefinition = "TEXT")
    private String regulatoryImpactSummary;

    @Column(name = "requires_human_validation", nullable = false)
    private Boolean requiresHumanValidation = true;

    @Column(name = "human_validation_status", nullable = false, length = 50)
    private String humanValidationStatus = "PENDING"; // PENDING, VALIDATED, REJECTED

    @Column(name = "validated_by")
    private UUID validatedBy;

    @Column(name = "validated_at")
    private OffsetDateTime validatedAt;

    @Column(name = "validation_notes", columnDefinition = "TEXT")
    private String validationNotes;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "affected_chunk_ids", columnDefinition = "jsonb")
    private String affectedChunkIds = "[]";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public PolicySectionDiff() {}

    public PolicySectionDiff(
        String tenantId,
        String policyCode,
        UUID baseDocumentId,
        UUID targetDocumentId,
        String sectionTitle,
        String changeType,
        String baseContent,
        String targetContent,
        String regulatoryImpactSummary,
        String affectedChunkIds
    ) {
        this.tenantId = tenantId != null ? tenantId : "default-bank";
        this.policyCode = policyCode;
        this.baseDocumentId = baseDocumentId;
        this.targetDocumentId = targetDocumentId;
        this.sectionTitle = sectionTitle;
        this.changeType = changeType;
        this.baseContent = baseContent;
        this.targetContent = targetContent;
        this.regulatoryImpactSummary = regulatoryImpactSummary;
        this.requiresHumanValidation = true;
        this.humanValidationStatus = "PENDING";
        this.affectedChunkIds = affectedChunkIds != null ? affectedChunkIds : "[]";
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getPolicyCode() { return policyCode; }
    public void setPolicyCode(String policyCode) { this.policyCode = policyCode; }

    public UUID getBaseDocumentId() { return baseDocumentId; }
    public void setBaseDocumentId(UUID baseDocumentId) { this.baseDocumentId = baseDocumentId; }

    public UUID getTargetDocumentId() { return targetDocumentId; }
    public void setTargetDocumentId(UUID targetDocumentId) { this.targetDocumentId = targetDocumentId; }

    public String getSectionTitle() { return sectionTitle; }
    public void setSectionTitle(String sectionTitle) { this.sectionTitle = sectionTitle; }

    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }

    public String getBaseContent() { return baseContent; }
    public void setBaseContent(String baseContent) { this.baseContent = baseContent; }

    public String getTargetContent() { return targetContent; }
    public void setTargetContent(String targetContent) { this.targetContent = targetContent; }

    public String getRegulatoryImpactSummary() { return regulatoryImpactSummary; }
    public void setRegulatoryImpactSummary(String regulatoryImpactSummary) { this.regulatoryImpactSummary = regulatoryImpactSummary; }

    public Boolean getRequiresHumanValidation() { return requiresHumanValidation; }
    public void setRequiresHumanValidation(Boolean requiresHumanValidation) { this.requiresHumanValidation = requiresHumanValidation; }

    public String getHumanValidationStatus() { return humanValidationStatus; }
    public void setHumanValidationStatus(String humanValidationStatus) { this.humanValidationStatus = humanValidationStatus; }

    public UUID getValidatedBy() { return validatedBy; }
    public void setValidatedBy(UUID validatedBy) { this.validatedBy = validatedBy; }

    public OffsetDateTime getValidatedAt() { return validatedAt; }
    public void setValidatedAt(OffsetDateTime validatedAt) { this.validatedAt = validatedAt; }

    public String getValidationNotes() { return validationNotes; }
    public void setValidationNotes(String validationNotes) { this.validationNotes = validationNotes; }

    public String getAffectedChunkIds() { return affectedChunkIds; }
    public void setAffectedChunkIds(String affectedChunkIds) { this.affectedChunkIds = affectedChunkIds; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
