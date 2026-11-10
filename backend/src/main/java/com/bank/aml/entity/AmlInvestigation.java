package com.bank.aml.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "aml_investigations")
public class AmlInvestigation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "alert_id", nullable = false, unique = true)
    private AmlAlert alert;

    @Column(name = "investigation_reference", nullable = false, unique = true, length = 64)
    private String investigationReference;

    @Column(name = "assigned_analyst", length = 128)
    private String assignedAnalyst;

    @Column(length = 128)
    private String reviewer;

    @Column(nullable = false, length = 32)
    private String status = "PENDING_ANALYSIS";

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "policy_relevance_explanation", columnDefinition = "TEXT")
    private String policyRelevanceExplanation;

    @Column(name = "sar_narrative_draft", columnDefinition = "TEXT")
    private String sarNarrativeDraft;

    @Column(name = "decision_recommendation", length = 64)
    private String decisionRecommendation;

    @Column(name = "analyst_decision", length = 64)
    private String analystDecision;

    @Column(name = "analyst_notes", columnDefinition = "TEXT")
    private String analystNotes;

    @Column(name = "reviewer_decision", length = 64)
    private String reviewerDecision;

    @Column(name = "reviewer_notes", columnDefinition = "TEXT")
    private String reviewerNotes;

    @Column(name = "escalation_reason", columnDefinition = "TEXT")
    private String escalationReason;

    @Column(name = "linked_policies", columnDefinition = "TEXT")
    private String linkedPolicies;

    @Column(name = "linked_audit_hashes", columnDefinition = "TEXT")
    private String linkedAuditHashes;

    @Column(name = "evidence_quality_score")
    private Double evidenceQualityScore = 0.0;

    @Column(name = "evidence_level", length = 32)
    private String evidenceLevel = "MEDIUM";

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "investigation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("recordedAt ASC")
    private List<InvestigationTimeline> timeline = new ArrayList<>();

    public AmlInvestigation() {}

    public AmlInvestigation(AmlAlert alert, String investigationReference, String assignedAnalyst,
                            String status, String tenantId) {
        this.alert = alert;
        this.investigationReference = investigationReference;
        this.assignedAnalyst = assignedAnalyst;
        this.status = status;
        this.tenantId = tenantId;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public AmlAlert getAlert() { return alert; }
    public void setAlert(AmlAlert alert) { this.alert = alert; }

    public String getInvestigationReference() { return investigationReference; }
    public void setInvestigationReference(String investigationReference) { this.investigationReference = investigationReference; }

    public String getAssignedAnalyst() { return assignedAnalyst; }
    public void setAssignedAnalyst(String assignedAnalyst) { this.assignedAnalyst = assignedAnalyst; }

    public String getReviewer() { return reviewer; }
    public void setReviewer(String reviewer) { this.reviewer = reviewer; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAiSummary() { return aiSummary; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }

    public String getPolicyRelevanceExplanation() { return policyRelevanceExplanation; }
    public void setPolicyRelevanceExplanation(String policyRelevanceExplanation) { this.policyRelevanceExplanation = policyRelevanceExplanation; }

    public String getSarNarrativeDraft() { return sarNarrativeDraft; }
    public void setSarNarrativeDraft(String sarNarrativeDraft) { this.sarNarrativeDraft = sarNarrativeDraft; }

    public String getDecisionRecommendation() { return decisionRecommendation; }
    public void setDecisionRecommendation(String decisionRecommendation) { this.decisionRecommendation = decisionRecommendation; }

    public String getAnalystDecision() { return analystDecision; }
    public void setAnalystDecision(String analystDecision) { this.analystDecision = analystDecision; }

    public String getAnalystNotes() { return analystNotes; }
    public void setAnalystNotes(String analystNotes) { this.analystNotes = analystNotes; }

    public String getReviewerDecision() { return reviewerDecision; }
    public void setReviewerDecision(String reviewerDecision) { this.reviewerDecision = reviewerDecision; }

    public String getReviewerNotes() { return reviewerNotes; }
    public void setReviewerNotes(String reviewerNotes) { this.reviewerNotes = reviewerNotes; }

    public String getEscalationReason() { return escalationReason; }
    public void setEscalationReason(String escalationReason) { this.escalationReason = escalationReason; }

    public String getLinkedPolicies() { return linkedPolicies; }
    public void setLinkedPolicies(String linkedPolicies) { this.linkedPolicies = linkedPolicies; }

    public String getLinkedAuditHashes() { return linkedAuditHashes; }
    public void setLinkedAuditHashes(String linkedAuditHashes) { this.linkedAuditHashes = linkedAuditHashes; }

    public Double getEvidenceQualityScore() { return evidenceQualityScore; }
    public void setEvidenceQualityScore(Double evidenceQualityScore) { this.evidenceQualityScore = evidenceQualityScore; }

    public String getEvidenceLevel() { return evidenceLevel; }
    public void setEvidenceLevel(String evidenceLevel) { this.evidenceLevel = evidenceLevel; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<InvestigationTimeline> getTimeline() { return timeline; }
    public void setTimeline(List<InvestigationTimeline> timeline) { this.timeline = timeline; }

    public void addTimelineEntry(InvestigationTimeline entry) {
        timeline.add(entry);
        entry.setInvestigation(this);
    }
}
