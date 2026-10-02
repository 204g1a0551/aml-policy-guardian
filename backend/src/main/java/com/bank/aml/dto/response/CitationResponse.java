package com.bank.aml.dto.response;

import java.util.UUID;

public record CitationResponse(
    UUID documentId,
    String documentTitle,
    String section,
    Integer pageNumber,
    Double similarity
) {}
