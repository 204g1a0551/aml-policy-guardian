package com.bank.aml.dto.response;

import com.bank.aml.entity.PolicySectionDiff;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SectionDiffResponse(
    UUID id,
    String policyCode,
    UUID baseDocumentId,
    UUID targetDocumentId,
    String baseVersion,
    String targetVersion,
    String sectionTitle,
    String changeType,
    String baseContent,
    String targetContent,
    String regulatoryImpactSummary,
    Boolean requiresHumanValidation,
    String humanValidationStatus,
    UUID validatedBy,
    OffsetDateTime validatedAt,
    String validationNotes,
    List<String> affectedChunkIds,
    OffsetDateTime createdAt
) {
    public static SectionDiffResponse fromEntity(PolicySectionDiff diff, String baseVersion, String targetVersion, List<String> chunkIds) {
        return new SectionDiffResponse(
            diff.getId(),
            diff.getPolicyCode(),
            diff.getBaseDocumentId(),
            diff.getTargetDocumentId(),
            baseVersion,
            targetVersion,
            diff.getSectionTitle(),
            diff.getChangeType(),
            diff.getBaseContent(),
            diff.getTargetContent(),
            diff.getRegulatoryImpactSummary(),
            diff.getRequiresHumanValidation(),
            diff.getHumanValidationStatus(),
            diff.getValidatedBy(),
            diff.getValidatedAt(),
            diff.getValidationNotes(),
            chunkIds,
            diff.getCreatedAt()
        );
    }
}
