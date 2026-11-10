package com.bank.aml.unit;

import com.bank.aml.entity.IngestionJob;
import com.bank.aml.entity.KafkaProcessedEvent;
import com.bank.aml.kafka.DocumentEvent;
import com.bank.aml.kafka.DocumentEventPublisher;
import com.bank.aml.kafka.DocumentIngestionConsumer;
import com.bank.aml.kafka.KafkaTopics;
import com.bank.aml.repository.KafkaProcessedEventRepository;
import com.bank.aml.service.IngestionJobService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DocumentKafkaProducerConsumerUnitTest {

    private KafkaTemplate<String, DocumentEvent> kafkaTemplate;
    private SimpleMeterRegistry meterRegistry;
    private DocumentEventPublisher eventPublisher;

    private IngestionJobService ingestionJobService;
    private KafkaProcessedEventRepository processedEventRepository;
    private DocumentIngestionConsumer ingestionConsumer;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        meterRegistry = new SimpleMeterRegistry();
        eventPublisher = new DocumentEventPublisher(kafkaTemplate, meterRegistry);

        ingestionJobService = mock(IngestionJobService.class);
        processedEventRepository = mock(KafkaProcessedEventRepository.class);
        ingestionConsumer = new DocumentIngestionConsumer(ingestionJobService, processedEventRepository, meterRegistry);
    }

    @Test
    void testPublisherSendsMessageWithPartitionKey() {
        DocumentEvent event = new DocumentEvent(
            "INGESTION_REQUESTED",
            UUID.randomUUID(),
            UUID.randomUUID(),
            "tenant-alpha",
            "AML-SOP-001",
            "policy.pdf",
            1024L,
            0,
            null,
            "corr-123"
        );

        RecordMetadata metadata = new RecordMetadata(new TopicPartition(KafkaTopics.INGESTION_REQUESTED, 0), 0, 0, 0, 0, 0);
        SendResult<String, DocumentEvent> sendResult = new SendResult<>(null, metadata);
        when(kafkaTemplate.send(eq(KafkaTopics.INGESTION_REQUESTED), eq("tenant-alpha"), eq(event)))
            .thenReturn(CompletableFuture.completedFuture(sendResult));

        CompletableFuture<Void> future = eventPublisher.publishEvent(KafkaTopics.INGESTION_REQUESTED, event);
        assertNotNull(future);
        assertTrue(future.isDone());

        verify(kafkaTemplate, times(1)).send(KafkaTopics.INGESTION_REQUESTED, "tenant-alpha", event);
        assertEquals(1.0, meterRegistry.get("kafka.events.published").counter().count());
    }

    @Test
    void testPublisherFallbackWhenKafkaDisabled() {
        DocumentEventPublisher fallbackPublisher = new DocumentEventPublisher(null, meterRegistry);
        assertFalse(fallbackPublisher.isKafkaEnabled());

        DocumentEvent event = new DocumentEvent(
            "DOCUMENT_UPLOADED",
            UUID.randomUUID(),
            UUID.randomUUID(),
            "default-bank",
            "AML-KYC-002",
            "kyc.docx",
            2048L,
            0,
            null,
            "corr-456"
        );

        CompletableFuture<Void> future = fallbackPublisher.publishEvent(KafkaTopics.DOCUMENT_UPLOADED, event);
        assertNotNull(future);
        assertTrue(future.isDone());
    }

    @Test
    void testConsumerProcessesIngestionEventAndRecordsToken() {
        UUID jobId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        DocumentEvent event = new DocumentEvent(
            "INGESTION_REQUESTED",
            jobId,
            docId,
            "tenant-beta",
            "AML-POL-003",
            "rulebook.txt",
            512L,
            0,
            null,
            "corr-789"
        );

        when(processedEventRepository.existsByEventId(event.getEventId())).thenReturn(false);

        ingestionConsumer.consumeIngestionRequest(event, KafkaTopics.INGESTION_REQUESTED, 1, 42L);

        verify(ingestionJobService, times(1)).executeJob(jobId);
        ArgumentCaptor<KafkaProcessedEvent> tokenCaptor = ArgumentCaptor.forClass(KafkaProcessedEvent.class);
        verify(processedEventRepository, times(1)).save(tokenCaptor.capture());

        assertEquals(event.getEventId(), tokenCaptor.getValue().getEventId());
        assertEquals("tenant-beta", tokenCaptor.getValue().getTenantId());
        assertEquals(KafkaTopics.INGESTION_REQUESTED, tokenCaptor.getValue().getTopic());
    }

    @Test
    void testConsumerDiscardsDuplicateEventIdempotently() {
        DocumentEvent duplicateEvent = new DocumentEvent(
            "INGESTION_REQUESTED",
            UUID.randomUUID(),
            UUID.randomUUID(),
            "tenant-beta",
            "AML-POL-003",
            "rulebook.txt",
            512L,
            0,
            null,
            "corr-dup"
        );

        when(processedEventRepository.existsByEventId(duplicateEvent.getEventId())).thenReturn(true);

        ingestionConsumer.consumeIngestionRequest(duplicateEvent, KafkaTopics.INGESTION_REQUESTED, 0, 100L);

        // Ingestion must NOT be triggered for duplicate message
        verify(ingestionJobService, never()).executeJob(any());
        verify(processedEventRepository, never()).save(any());
        assertEquals(1.0, meterRegistry.get("kafka.consumer.duplicates.discarded").counter().count());
    }

    @Test
    void testConsumerDeadLetterQueueHandler() {
        DocumentEvent dltEvent = new DocumentEvent(
            "INDEXING_DLT",
            UUID.randomUUID(),
            UUID.randomUUID(),
            "tenant-gamma",
            "AML-FAIL-004",
            "corrupted.pdf",
            0L,
            3,
            "Exceeded max retries: parser crash",
            "corr-dlt"
        );

        assertDoesNotThrow(() -> ingestionConsumer.consumeDeadLetterQueue(dltEvent, KafkaTopics.INDEXING_DLT));
        assertEquals(1.0, meterRegistry.get("kafka.ingestion.dlt.received").counter().count());
    }
}
