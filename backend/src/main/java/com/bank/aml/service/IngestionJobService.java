package com.bank.aml.service;

import com.bank.aml.document.DocumentIngestionPipeline;
import com.bank.aml.entity.Document;
import com.bank.aml.entity.IngestionJob;
import com.bank.aml.exception.ResourceNotFoundException;
import com.bank.aml.kafka.DocumentEvent;
import com.bank.aml.kafka.DocumentEventPublisher;
import com.bank.aml.kafka.KafkaTopics;
import com.bank.aml.repository.DocumentRepository;
import com.bank.aml.repository.IngestionJobRepository;
import com.bank.aml.security.tenant.TenantContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class IngestionJobService {

    private static final Logger log = LoggerFactory.getLogger(IngestionJobService.class);

    private final IngestionJobRepository ingestionJobRepository;
    private final DocumentRepository documentRepository;
    private final DocumentIngestionPipeline documentIngestionPipeline;
    private final DocumentEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    @Autowired
    public IngestionJobService(
        IngestionJobRepository ingestionJobRepository,
        DocumentRepository documentRepository,
        DocumentIngestionPipeline documentIngestionPipeline,
        DocumentEventPublisher eventPublisher,
        @Autowired(required = false) MeterRegistry meterRegistry
    ) {
        this.ingestionJobRepository = ingestionJobRepository;
        this.documentRepository = documentRepository;
        this.documentIngestionPipeline = documentIngestionPipeline;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public IngestionJob createJob(Document document, String correlationId) {
        String tenantId = document.getTenantId() != null ? document.getTenantId() : TenantContext.getTenantId();
        IngestionJob job = new IngestionJob(document.getId(), tenantId, correlationId);
        job.setStatus("QUEUED");
        job.setStage("QUEUED");
        job.setProgressPct(5);

        IngestionJob saved = ingestionJobRepository.save(job);
        recordCounter("ingestion.jobs.created", "tenant", tenantId);
        return saved;
    }

    @Transactional
    public void updateProgress(UUID jobId, String stage, int progressPct) {
        ingestionJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus("PROCESSING");
            job.setStage(stage);
            job.setProgressPct(progressPct);
            ingestionJobRepository.save(job);
        });
    }

    /**
     * Executes the actual ingestion pipeline work for a job, updating stage and progress,
     * and publishing INDEXING_COMPLETED or INDEXING_FAILED events.
     */
    public void executeJob(UUID jobId) {
        IngestionJob job = ingestionJobRepository.findById(jobId)
            .orElseThrow(() -> new ResourceNotFoundException("Ingestion job not found: " + jobId));

        Document document = documentRepository.findById(job.getDocumentId())
            .orElseThrow(() -> new ResourceNotFoundException("Document not found for job: " + job.getDocumentId()));

        log.info("Executing ingestion job: id={}, docId={}, tenant={}", jobId, document.getId(), job.getTenantId());

        try {
            updateProgress(jobId, "TEXT_EXTRACTION", 25);
            updateProgress(jobId, "CHUNKING", 50);
            updateProgress(jobId, "EMBEDDING_GENERATION", 75);

            // Execute core pipeline (Tika parse -> Semantic Chunking -> pgvector embeddings commit)
            documentIngestionPipeline.ingest(document.getId());

            updateProgress(jobId, "VECTOR_STORAGE", 90);

            // Mark job COMPLETED
            IngestionJob currentJob = ingestionJobRepository.findById(jobId).orElse(job);
            currentJob.setStatus("COMPLETED");
            currentJob.setStage("COMPLETED");
            currentJob.setProgressPct(100);
            currentJob.setCompletedAt(OffsetDateTime.now());
            currentJob.setErrorMessage(null);
            ingestionJobRepository.save(currentJob);

            recordCounter("ingestion.jobs.completed", "tenant", currentJob.getTenantId());

            // Publish completed event
            DocumentEvent completedEvent = new DocumentEvent(
                "INDEXING_COMPLETED",
                currentJob.getId(),
                document.getId(),
                currentJob.getTenantId(),
                document.getPolicyCode(),
                document.getFilename(),
                document.getFileSizeBytes(),
                currentJob.getRetryCount(),
                null,
                currentJob.getCorrelationId()
            );
            eventPublisher.publishEvent(KafkaTopics.INDEXING_COMPLETED, completedEvent);

        } catch (Exception ex) {
            log.error("Ingestion job failed: id={}, error={}", jobId, ex.getMessage(), ex);
            handleJobFailure(job, document, ex);
            throw new RuntimeException("Ingestion job failed: " + ex.getMessage(), ex);
        }
    }

    @Transactional
    public void handleJobFailure(IngestionJob job, Document document, Exception ex) {
        job.setStatus("FAILED");
        job.setStage("FAILED");
        job.setErrorMessage(ex.getMessage() != null ? ex.getMessage() : "Unknown ingestion pipeline error");
        job.setCompletedAt(OffsetDateTime.now());
        ingestionJobRepository.save(job);

        // Update document status
        document.setStatus("FAILED");
        document.setErrorMessage(job.getErrorMessage());
        documentRepository.save(document);

        recordCounter("ingestion.jobs.failed", "tenant", job.getTenantId());

        // Publish failed event
        DocumentEvent failedEvent = new DocumentEvent(
            "INDEXING_FAILED",
            job.getId(),
            document.getId(),
            job.getTenantId(),
            document.getPolicyCode(),
            document.getFilename(),
            document.getFileSizeBytes(),
            job.getRetryCount(),
            ex.getMessage(),
            job.getCorrelationId()
        );
        eventPublisher.publishEvent(KafkaTopics.INDEXING_FAILED, failedEvent);
    }

    @Transactional
    public IngestionJob retryJob(UUID documentId) {
        String tenantId = TenantContext.getTenantId();
        Document doc = documentRepository.findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        if (doc.getTenantId() != null && !doc.getTenantId().equals(tenantId)) {
            throw new AccessDeniedException("Cross-tenant access violation: Document does not belong to tenant " + tenantId);
        }

        doc.setStatus("PROCESSING");
        doc.setErrorMessage(null);
        documentRepository.save(doc);

        IngestionJob job = new IngestionJob(doc.getId(), tenantId, UUID.randomUUID().toString());
        job.setStatus("QUEUED");
        job.setStage("QUEUED");
        job.setProgressPct(5);
        job.setRetryCount(1);
        IngestionJob saved = ingestionJobRepository.save(job);

        DocumentEvent event = new DocumentEvent(
            "INGESTION_REQUESTED",
            saved.getId(),
            doc.getId(),
            tenantId,
            doc.getPolicyCode(),
            doc.getFilename(),
            doc.getFileSizeBytes(),
            1,
            null,
            saved.getCorrelationId()
        );

        eventPublisher.publishEvent(KafkaTopics.INGESTION_REQUESTED, event);

        // If Kafka is disabled, execute immediately
        if (!eventPublisher.isKafkaEnabled()) {
            executeJob(saved.getId());
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public IngestionJob getJobById(UUID jobId) {
        String tenantId = TenantContext.getTenantId();
        IngestionJob job = ingestionJobRepository.findById(jobId)
            .orElseThrow(() -> new ResourceNotFoundException("Ingestion job not found with ID: " + jobId));

        if (job.getTenantId() != null && !job.getTenantId().equals(tenantId)) {
            throw new AccessDeniedException("Cross-tenant access violation: Ingestion job does not belong to tenant " + tenantId);
        }

        return job;
    }

    @Transactional(readOnly = true)
    public Optional<IngestionJob> getLatestJobForDocument(UUID documentId) {
        String tenantId = TenantContext.getTenantId();
        return ingestionJobRepository.findTopByTenantIdAndDocumentIdOrderByCreatedAtDesc(tenantId, documentId);
    }

    @Transactional(readOnly = true)
    public List<IngestionJob> getJobsForCurrentTenant() {
        String tenantId = TenantContext.getTenantId();
        return ingestionJobRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    private void recordCounter(String name, String... tags) {
        if (meterRegistry != null) {
            try {
                Counter.builder(name).tags(tags).register(meterRegistry).increment();
            } catch (Exception ignored) {}
        }
    }
}
