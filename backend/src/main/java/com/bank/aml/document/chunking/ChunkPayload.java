package com.bank.aml.document.chunking;

import java.util.Map;

public record ChunkPayload(
    int chunkIndex,
    int pageNumber,
    String section,
    String text,
    Map<String, Object> metadata
) {}
