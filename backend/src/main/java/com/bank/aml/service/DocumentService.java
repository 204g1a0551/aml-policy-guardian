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
import com.bank.aml.security.tenant.TenantContext;
import com.bank.aml.security.upload.DocumentUploadValidator;
import com.bank.aml.util.FileValidatorService;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
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
    private final DocumentUploadValidator documentUploadValidator;
    private final com.bank.aml.service.IngestionJobService ingestionJobService;
    private final com.bank.aml.kafka.DocumentEventPublisher eventPublisher;
    private final com.bank.aml.cache.TenantAwareCacheService cacheService;
    private final Tika tika = new Tika();

    @Autowired
    public DocumentService(
        DocumentRepository documentRepository,
        DocumentChunkRepository documentChunkRepository,
        DocumentStorageService documentStorageService,
        DocumentIngestionPipeline documentIngestionPipeline,
        FileValidatorService fileValidatorService,
        DocumentUploadValidator documentUploadValidator,
        @Autowired(required = false) com.bank.aml.service.IngestionJobService ingestionJobService,
        @Autowired(required = false) com.bank.aml.kafka.DocumentEventPublisher eventPublisher,
        @Autowired(required = false) com.bank.aml.cache.TenantAwareCacheService cacheService
    ) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.documentStorageService = documentStorageService;
        this.documentIngestionPipeline = documentIngestionPipeline;
        this.fileValidatorService = fileValidatorService;
        this.documentUploadValidator = documentUploadValidator;
        this.ingestionJobService = ingestionJobService;
        this.eventPublisher = eventPublisher;
        this.cacheService = cacheService;
    }

    public DocumentService(
        DocumentRepository documentRepository,
        DocumentChunkRepository documentChunkRepository,
        DocumentStorageService documentStorageService,
        DocumentIngestionPipeline documentIngestionPipeline,
        FileValidatorService fileValidatorService,
        DocumentUploadValidator documentUploadValidator
    ) {
        this(documentRepository, documentChunkRepository, documentStorageService, documentIngestionPipeline, fileValidatorService, documentUploadValidator, null, null, null);
    }

    public DocumentService(
        DocumentRepository documentRepository,
        DocumentChunkRepository documentChunkRepository,
        DocumentStorageService documentStorageService,
        DocumentIngestionPipeline documentIngestionPipeline,
        FileValidatorService fileValidatorService
    ) {
        this(documentRepository, documentChunkRepository, documentStorageService, documentIngestionPipeline, fileValidatorService, null, null, null, null);
    }

    public DocumentResponse uploadDocument(MultipartFile file, DocumentUploadRequest request, UUID uploadedBy) {
        String tenantId = TenantContext.getTenantId();

        // 1. Antivirus and Deep Magic-Byte Inspection
        String sanitizedFilename;
        String mimeType;
        byte[] fileBytes;
        if (documentUploadValidator != null) {
            DocumentUploadValidator.ValidatedUpload validated = documentUploadValidator.validateAndScan(file);
            sanitizedFilename = validated.sanitizedFilename();
            mimeType = validated.detectedMimeType();
            fileBytes = validated.fileBytes();
        } else {
            fileValidatorService.validateFile(file);
            sanitizedFilename = fileValidatorService.sanitizeFilename(file.getOriginalFilename());
            try {
                fileBytes = file.getBytes();
                try (InputStream is = new ByteArrayInputStream(fileBytes)) {
                    mimeType = tika.detect(is, sanitizedFilename);
                }
            } catch (IOException e) {
                mimeType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
                try {
                    fileBytes = file.getBytes();
                } catch (IOException ex) {
                    throw new RuntimeException("Failed to read file: " + ex.getMessage(), ex);
                }
            }
        }

        // 2. Duplicate Detection via SHA-256 within the current tenant
        String sha256 = fileValidatorService.computeSha256(file);
        if (documentRepository.existsByTenantIdAndSha256ChecksumAndStatusNot(tenantId, sha256, "DELETED")) {
            throw new DuplicateDocumentException("A document with identical content already exists in the compliance repository.");
        }

        // 3. Store binary safely using storage abstraction
        String storagePath;
        try (InputStream is = new ByteArrayInputStream(fileBytes)) {
            storagePath = documentStorageService.store(is, sanitizedFilename, mimeType);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file input stream: " + e.getMessage(), e);
        }

        // 4. Persist Document metadata entity with tenant isolation
        Document document = new Document();
        document.setTenantId(tenantId);
        document.setTitle(request.title());
        document.setFilename(sanitizedFilename);
        document.setDocumentType(request.documentType());
        document.setVersion(request.version());
        document.setSource(request.source());
        document.setStatus("UPLOADED");
        document.setUploadedBy(uploadedBy);
        document.setFileSizeBytes((long) fileBytes.length);
        document.setSha256Checksum(sha256);
        document.setStoragePath(storagePath);
        document.setMimeType(mimeType);

        String policyCode = request.policyCode();
        if (policyCode == null || policyCode.isBlank()) {
            var matcher = java.util.regex.Pattern.compile("^([A-Z0-9]+-[A-Z0-9]+-[0-9]+)").matcher(sanitizedFilename);
            if (matcher.find()) {
                policyCode = matcher.group(1);
            } else {
                policyCode = sanitizedFilename.replaceFirst("\\.[^.]+$", "");
            }
        }
        document.setPolicyCode(policyCode);
        document.setJurisdiction(request.jurisdiction() != null ? request.jurisdiction() : "GLOBAL");
        document.setEffectiveDate(request.effectiveDate() != null ? request.effectiveDate() : java.time.OffsetDateTime.now());
        document.setApprovalStatus(request.approvalStatus() != null ? request.approvalStatus() : "APPROVED");
        document.setIsEffective("APPROVED".equalsIgnoreCase(document.getApprovalStatus()));

        Document savedDoc = documentRepository.save(document);

        // 5. Create Ingestion Job and initiate pipeline
        com.bank.aml.entity.IngestionJob job = null;
        if (ingestionJobService != null) {
            job = ingestionJobService.createJob(savedDoc, UUID.randomUUID().toString());
        }

        // Publish DOCUMENT_UPLOADED event
        if (eventPublisher != null && job != null) {
            com.bank.aml.kafka.DocumentEvent uploadedEvent = new com.bank.aml.kafka.DocumentEvent(
                "DOCUMENT_UPLOADED",
                job.getId(),
                savedDoc.getId(),
                savedDoc.getTenantId(),
                savedDoc.getPolicyCode(),
                savedDoc.getFilename(),
                savedDoc.getFileSizeBytes(),
                0,
                null,
                job.getCorrelationId()
            );
            eventPublisher.publishEvent(com.bank.aml.kafka.KafkaTopics.DOCUMENT_UPLOADED, uploadedEvent);
        }

        // Execute Ingestion: Asynchronous via Kafka if enabled, or synchronous execution fallback
        boolean asyncIngestion = eventPublisher != null && eventPublisher.isKafkaEnabled();
        if (asyncIngestion && job != null) {
            com.bank.aml.kafka.DocumentEvent ingestionEvent = new com.bank.aml.kafka.DocumentEvent(
                "INGESTION_REQUESTED",
                job.getId(),
                savedDoc.getId(),
                savedDoc.getTenantId(),
                savedDoc.getPolicyCode(),
                savedDoc.getFilename(),
                savedDoc.getFileSizeBytes(),
                0,
                null,
                job.getCorrelationId()
            );
            eventPublisher.publishEvent(com.bank.aml.kafka.KafkaTopics.INGESTION_REQUESTED, ingestionEvent);
        } else {
            if (ingestionJobService != null && job != null) {
                ingestionJobService.executeJob(job.getId());
            } else {
                documentIngestionPipeline.ingest(savedDoc.getId());
            }
        }

        // Evict tenant caches on document upload
        if (cacheService != null) {
            cacheService.evictNamespace("retrieval");
            cacheService.evictNamespace("policies");
        }

        // Reload refreshed document entity after pipeline execution (status will be READY, PROCESSING, or FAILED)
        Document refreshed = documentRepository.findById(savedDoc.getId()).orElse(savedDoc);
        return DocumentResponse.fromEntity(refreshed);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> getAllDocuments(String status, String documentType) {
        String tenantId = TenantContext.getTenantId();
        List<Document> docs;
        if (status != null && !status.isBlank()) {
            docs = documentRepository.findByTenantIdAndStatus(tenantId, status.toUpperCase());
        } else if (documentType != null && !documentType.isBlank()) {
            docs = documentRepository.findByTenantIdAndDocumentTypeAndStatusNot(tenantId, documentType.toUpperCase(), "DELETED");
        } else {
            docs = documentRepository.findByTenantIdAndStatusNot(tenantId, "DELETED");
        }

        return docs.stream().map(DocumentResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public DocumentResponse getDocumentById(UUID id) {
        Document doc = documentRepository.findById(id)
            .filter(d -> !"DELETED".equalsIgnoreCase(d.getStatus()))
            .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + id));

        // Object-level authorization / Tenant isolation check
        String currentTenant = TenantContext.getTenantId();
        if (doc.getTenantId() != null && !doc.getTenantId().equals(currentTenant)) {
            throw new AccessDeniedException("Cross-tenant access violation: Document does not belong to tenant " + currentTenant);
        }

        return DocumentResponse.fromEntity(doc);
    }

    @Transactional
    public void deleteDocument(UUID id) {
        Document doc = documentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + id));

        // Object-level authorization / Tenant isolation check
        String currentTenant = TenantContext.getTenantId();
        if (doc.getTenantId() != null && !doc.getTenantId().equals(currentTenant)) {
            throw new AccessDeniedException("Cross-tenant access violation: Cannot delete document belonging to tenant " + doc.getTenantId());
        }

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

        // Evict tenant caches on document deletion
        if (cacheService != null) {
            cacheService.evictNamespace("retrieval");
            cacheService.evictNamespace("policies");
        }
    }

    @Transactional(readOnly = true)
    public com.bank.aml.entity.IngestionJob getIngestionJob(UUID jobId) {
        if (ingestionJobService == null) {
            throw new ResourceNotFoundException("Ingestion job service not configured");
        }
        return ingestionJobService.getJobById(jobId);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<com.bank.aml.entity.IngestionJob> getLatestJobForDocument(UUID documentId) {
        if (ingestionJobService == null) {
            return java.util.Optional.empty();
        }
        return ingestionJobService.getLatestJobForDocument(documentId);
    }

    @Transactional
    public com.bank.aml.entity.IngestionJob retryIngestion(UUID documentId) {
        if (ingestionJobService == null) {
            throw new ResourceNotFoundException("Ingestion job service not configured");
        }
        return ingestionJobService.retryJob(documentId);
    }
}
