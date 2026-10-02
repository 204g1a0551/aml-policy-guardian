package com.bank.aml.service;

import com.bank.aml.document.DocumentIngestionPipeline;
import com.bank.aml.document.DocumentStorageService;
import com.bank.aml.dto.request.DocumentUploadRequest;
import com.bank.aml.dto.response.DocumentResponse;
import com.bank.aml.entity.Document;
import com.bank.aml.exception.DuplicateDocumentException;
import com.bank.aml.exception.ResourceNotFoundException;
import com.bank.aml.repository.DocumentChunkRepository;
import com.bank.aml.repository.DocumentRepository;
import com.bank.aml.util.FileValidatorService;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentStorageService documentStorageService;
    private final DocumentIngestionPipeline documentIngestionPipeline;
    private final FileValidatorService fileValidatorService;
    private final Tika tika = new Tika();

    public DocumentService(
        DocumentRepository documentRepository,
        DocumentChunkRepository documentChunkRepository,
        DocumentStorageService documentStorageService,
        DocumentIngestionPipeline documentIngestionPipeline,
        FileValidatorService fileValidatorService
    ) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.documentStorageService = documentStorageService;
        this.documentIngestionPipeline = documentIngestionPipeline;
        this.fileValidatorService = fileValidatorService;
    }

    public DocumentResponse uploadDocument(MultipartFile file, DocumentUploadRequest request, UUID uploadedBy) {
        // 1. Validation (extension, size, content MIME)
        fileValidatorService.validateFile(file);

        // 2. Duplicate Detection via SHA-256
        String sha256 = fileValidatorService.computeSha256(file);
        if (documentRepository.existsBySha256ChecksumAndStatusNot(sha256, "DELETED")) {
            throw new DuplicateDocumentException("A document with identical content already exists in the compliance repository.");
        }

        // 3. Filename sanitization
        String sanitizedFilename = fileValidatorService.sanitizeFilename(file.getOriginalFilename());

        // 4. Detect exact MIME
        String mimeType;
        try (InputStream is = file.getInputStream()) {
            mimeType = tika.detect(is, sanitizedFilename);
        } catch (IOException e) {
            mimeType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        }

        // 5. Store binary safely using storage abstraction
        String storagePath;
        try (InputStream is = file.getInputStream()) {
            storagePath = documentStorageService.store(is, sanitizedFilename, mimeType);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file input stream: " + e.getMessage(), e);
        }

        // 6. Persist Document metadata entity with UPLOADED status
        Document document = new Document();
        document.setTitle(request.title());
        document.setFilename(sanitizedFilename);
        document.setDocumentType(request.documentType());
        document.setVersion(request.version());
        document.setSource(request.source());
        document.setStatus("UPLOADED");
        document.setUploadedBy(uploadedBy);
        document.setFileSizeBytes(file.getSize());
        document.setSha256Checksum(sha256);
        document.setStoragePath(storagePath);
        document.setMimeType(mimeType);

        Document savedDoc = documentRepository.save(document);

        // 7. Execute complete RAG ingestion pipeline (Text -> Metadata -> Chunks -> Embeddings -> pgvector)
        documentIngestionPipeline.ingest(savedDoc.getId());

        // Reload refreshed document entity after pipeline completion (status will be READY or FAILED)
        Document refreshed = documentRepository.findById(savedDoc.getId()).orElse(savedDoc);
        return DocumentResponse.fromEntity(refreshed);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> getAllDocuments(String status, String documentType) {
        List<Document> docs;
        if (status != null && !status.isBlank()) {
            docs = documentRepository.findByStatus(status.toUpperCase());
        } else if (documentType != null && !documentType.isBlank()) {
            docs = documentRepository.findByDocumentTypeAndStatusNot(documentType.toUpperCase(), "DELETED");
        } else {
            docs = documentRepository.findByStatusNot("DELETED");
        }

        return docs.stream().map(DocumentResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public DocumentResponse getDocumentById(UUID id) {
        Document doc = documentRepository.findById(id)
            .filter(d -> !"DELETED".equalsIgnoreCase(d.getStatus()))
            .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + id));

        return DocumentResponse.fromEntity(doc);
    }

    @Transactional
    public void deleteDocument(UUID id) {
        Document doc = documentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + id));

        if ("DELETED".equalsIgnoreCase(doc.getStatus())) {
            return;
        }

        // Remove stored binary from disk or S3
        documentStorageService.delete(doc.getStoragePath());

        // Remove chunks and vectors from PostgreSQL pgvector
        documentChunkRepository.deleteByDocumentId(id);

        // Mark as DELETED (Soft delete in repository)
        doc.setStatus("DELETED");
        documentRepository.save(doc);
    }
}
