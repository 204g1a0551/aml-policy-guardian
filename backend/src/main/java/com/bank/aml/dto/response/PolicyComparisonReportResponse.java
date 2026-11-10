package com.bank.aml.dto.response;

import java.util.List;
import java.util.UUID;

public record PolicyComparisonReportResponse(
    String policyCode,
    UUID baseDocumentId,
    String baseVersion,
    String baseTitle,
    UUID targetDocumentId,
    String targetVersion,
    String targetTitle,
    ComparisonMetrics metrics,
    List<SectionDiffResponse> diffs
) {
    public record ComparisonMetrics(
        int totalSections,
        int additionsCount,
        int deletionsCount,
        int modifiedObligationsCount,
        int unchangedCount,
        int pendingHumanValidationCount
    ) {}
}
