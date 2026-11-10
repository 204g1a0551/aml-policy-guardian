package com.bank.aml.dto.response;

import java.util.UUID;

public record CitationResponse(
    UUID documentId,
    String documentTitle,
    String section,
    Integer pageNumber,
    Double similarity,
    String snippet,
    Double citationSupportScore,
    Boolean verified,
    String verificationReason
) {
    public CitationResponse(UUID documentId, String documentTitle, String section, Integer pageNumber, Double similarity) {
        this(documentId, documentTitle, section, pageNumber, similarity, null, null, null, null);
    }
}
