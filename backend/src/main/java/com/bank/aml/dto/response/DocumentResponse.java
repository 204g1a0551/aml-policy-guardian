package com.bank.aml.dto.response;

import com.bank.aml.entity.Document;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentResponse(
    UUID id,
    String title,
    String filename,
    String documentType,
    String version,
    String source,
    String status,
    Long fileSizeBytes,
    String sha256Checksum,
    String mimeType,
    String errorMessage,
    UUID uploadedBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static DocumentResponse fromEntity(Document doc) {
        return new DocumentResponse(
            doc.getId(),
            doc.getTitle(),
            doc.getFilename(),
            doc.getDocumentType(),
            doc.getVersion(),
            doc.getSource(),
            doc.getStatus(),
            doc.getFileSizeBytes(),
            doc.getSha256Checksum(),
            doc.getMimeType(),
            doc.getErrorMessage(),
            doc.getUploadedBy(),
            doc.getCreatedAt(),
            doc.getUpdatedAt()
        );
    }
}
