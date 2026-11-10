package com.bank.aml.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AmlInvestigationDetailResponse(
    UUID id,
    String investigationReference,
    AmlAlertResponse alert,
    String assignedAnalyst,
    String reviewer,
    String status,
    String aiSummary,
    String policyRelevanceExplanation,
    String sarNarrativeDraft,
    String decisionRecommendation,
    String analystDecision,
    String analystNotes,
    String reviewerDecision,
    String reviewerNotes,
    String escalationReason,
    List<LinkedPolicyDto> linkedPolicies,
    List<String> linkedAuditHashes,
    Double evidenceQualityScore,
    String evidenceLevel,
    String tenantId,
    OffsetDateTime completedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    List<InvestigationTimelineResponse> timeline
) {}
