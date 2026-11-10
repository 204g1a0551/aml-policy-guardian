package com.bank.aml.document.chunking;

import java.util.Map;

public record ChunkPayload(
    int chunkIndex,
    int pageNumber,
    String section,
    String text,
    Map<String, Object> metadata,
    Integer parentIndex,
    boolean isParent,
    String summary
) {
    public ChunkPayload(int chunkIndex, int pageNumber, String section, String text, Map<String, Object> metadata) {
        this(chunkIndex, pageNumber, section, text, metadata, null, false, null);
    }
}
