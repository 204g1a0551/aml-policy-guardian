package com.bank.aml.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "aml_investigation_timeline")
public class InvestigationTimeline {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "investigation_id", nullable = false)
    private AmlInvestigation investigation;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "performed_by", nullable = false, length = 128)
    private String performedBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "previous_status", length = 32)
    private String previousStatus;

    @Column(name = "new_status", length = 32)
    private String newStatus;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private OffsetDateTime recordedAt = OffsetDateTime.now();

    public InvestigationTimeline() {}

    public InvestigationTimeline(AmlInvestigation investigation, String action, String performedBy,
                                 String notes, String previousStatus, String newStatus) {
        this.investigation = investigation;
        this.action = action;
        this.performedBy = performedBy;
        this.notes = notes;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.recordedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public AmlInvestigation getInvestigation() { return investigation; }
    public void setInvestigation(AmlInvestigation investigation) { this.investigation = investigation; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }

    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }

    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime recordedAt) { this.recordedAt = recordedAt; }
}
