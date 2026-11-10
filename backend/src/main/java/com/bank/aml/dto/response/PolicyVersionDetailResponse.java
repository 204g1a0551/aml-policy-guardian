package com.bank.aml.dto.response;

import com.bank.aml.entity.Document;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PolicyVersionDetailResponse(
    UUID id,
    String policyCode,
    String version,
    String title,
    String filename,
    String documentType,
    String jurisdiction,
    OffsetDateTime effectiveDate,
    String approvalStatus,
    Boolean isEffective,
    UUID approvedBy,
    OffsetDateTime approvedAt,
    UUID supersededBy,
    OffsetDateTime supersededAt,
    String sha256Checksum,
    Long fileSizeBytes,
    OffsetDateTime createdAt
) {
    public static PolicyVersionDetailResponse fromEntity(Document doc) {
        return new PolicyVersionDetailResponse(
            doc.getId(),
            doc.getPolicyCode(),
            doc.getVersion(),
            doc.getTitle(),
            doc.getFilename(),
            doc.getDocumentType(),
            doc.getJurisdiction(),
            doc.getEffectiveDate(),
            doc.getApprovalStatus(),
            doc.getIsEffective(),
            doc.getApprovedBy(),
            doc.getApprovedAt(),
            doc.getSupersededBy(),
            doc.getSupersededAt(),
            doc.getSha256Checksum(),
            doc.getFileSizeBytes(),
            doc.getCreatedAt()
        );
    }
}
