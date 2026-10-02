package com.bank.aml.document.chunking;

import com.bank.aml.document.parser.ExtractedDocument;
import com.bank.aml.entity.Document;

import java.util.List;
import java.util.UUID;

public interface DocumentChunker {
    List<ChunkPayload> chunk(
        ExtractedDocument extractedDocument,
        UUID documentId,
        Document docMetadata,
        int chunkSizeTokens,
        int chunkOverlapTokens
    );
}
