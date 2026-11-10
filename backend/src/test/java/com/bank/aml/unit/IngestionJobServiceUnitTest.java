package com.bank.aml.unit;

import com.bank.aml.document.DocumentIngestionPipeline;
import com.bank.aml.entity.Document;
import com.bank.aml.entity.IngestionJob;
import com.bank.aml.kafka.DocumentEvent;
import com.bank.aml.kafka.DocumentEventPublisher;
import com.bank.aml.kafka.KafkaTopics;
import com.bank.aml.repository.DocumentRepository;
import com.bank.aml.repository.IngestionJobRepository;
import com.bank.aml.security.tenant.TenantContext;
import com.bank.aml.service.IngestionJobService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IngestionJobServiceUnitTest {

    private IngestionJobRepository jobRepository;
    private DocumentRepository documentRepository;
    private DocumentIngestionPipeline ingestionPipeline;
    private DocumentEventPublisher eventPublisher;
    private SimpleMeterRegistry meterRegistry;
    private IngestionJobService jobService;

    @BeforeEach
    void setUp() {
        jobRepository = mock(IngestionJobRepository.class);
        documentRepository = mock(DocumentRepository.class);
        ingestionPipeline = mock(DocumentIngestionPipeline.class);
        eventPublisher = mock(DocumentEventPublisher.class);
        meterRegistry = new SimpleMeterRegistry();

        jobService = new IngestionJobService(
            jobRepository,
            documentRepository,
            ingestionPipeline,
            eventPublisher,
            meterRegistry
        );
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testCreateJobInitializesQueuedState() {
        TenantContext.setTenantId("tenant-x");
        Document doc = new Document();
        doc.setId(UUID.randomUUID());
        doc.setTenantId("tenant-x");

        when(jobRepository.save(any(IngestionJob.class))).thenAnswer(invocation -> {
            IngestionJob job = invocation.getArgument(0);
            job.setId(UUID.randomUUID());
            return job;
        });

        IngestionJob created = jobService.createJob(doc, "corr-abc");
        assertNotNull(created);
        assertEquals("QUEUED", created.getStatus());
        assertEquals("QUEUED", created.getStage());
        assertEquals(5, created.getProgressPct());
        assertEquals("tenant-x", created.getTenantId());
        assertEquals("corr-abc", created.getCorrelationId());
        assertEquals(1.0, meterRegistry.get("ingestion.jobs.created").counter().count());
    }

    @Test
    void testExecuteJobSuccessfulFlow() {
        UUID jobId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        Document doc = new Document();
        doc.setId(docId);
        doc.setTitle("Transaction Monitoring SOP");
        doc.setFilename("sop.pdf");
        doc.setTenantId("tenant-x");
        doc.setPolicyCode("AML-SOP-001");
        doc.setFileSizeBytes(1024L);

        IngestionJob job = new IngestionJob(docId, "tenant-x", "corr-exec");
        job.setId(jobId);

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(jobRepository.save(any(IngestionJob.class))).thenAnswer(inv -> inv.getArgument(0));

        jobService.executeJob(jobId);

        verify(ingestionPipeline, times(1)).ingest(docId);
        assertEquals("COMPLETED", job.getStatus());
        assertEquals("COMPLETED", job.getStage());
        assertEquals(100, job.getProgressPct());
        assertNotNull(job.getCompletedAt());
        assertNull(job.getErrorMessage());

        ArgumentCaptor<DocumentEvent> eventCaptor = ArgumentCaptor.forClass(DocumentEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eq(KafkaTopics.INDEXING_COMPLETED), eventCaptor.capture());
        assertEquals("INDEXING_COMPLETED", eventCaptor.getValue().getEventType());
        assertEquals(jobId, eventCaptor.getValue().getJobId());
        assertEquals(1.0, meterRegistry.get("ingestion.jobs.completed").counter().count());
    }

    @Test
    void testExecuteJobFailureFlow() {
        UUID jobId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        Document doc = new Document();
        doc.setId(docId);
        doc.setTenantId("tenant-x");

        IngestionJob job = new IngestionJob(docId, "tenant-x", "corr-fail");
        job.setId(jobId);

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(jobRepository.save(any(IngestionJob.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("Tika OCR timeout")).when(ingestionPipeline).ingest(docId);

        assertThrows(RuntimeException.class, () -> jobService.executeJob(jobId));

        assertEquals("FAILED", job.getStatus());
        assertEquals("FAILED", job.getStage());
        assertTrue(job.getErrorMessage().contains("Tika OCR timeout"));
        assertEquals("FAILED", doc.getStatus());

        verify(eventPublisher, times(1)).publishEvent(eq(KafkaTopics.INDEXING_FAILED), any(DocumentEvent.class));
        assertEquals(1.0, meterRegistry.get("ingestion.jobs.failed").counter().count());
    }

    @Test
    void testCrossTenantJobAccessDenied() {
        TenantContext.setTenantId("bank-1");
        UUID jobId = UUID.randomUUID();

        IngestionJob job = new IngestionJob(UUID.randomUUID(), "bank-2", "corr-foreign");
        job.setId(jobId);

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        assertThrows(AccessDeniedException.class, () -> jobService.getJobById(jobId));
    }
}
