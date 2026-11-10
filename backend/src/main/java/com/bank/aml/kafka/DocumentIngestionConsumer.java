package com.bank.aml.kafka;

import com.bank.aml.entity.KafkaProcessedEvent;
import com.bank.aml.repository.KafkaProcessedEventRepository;
import com.bank.aml.security.tenant.TenantContext;
import com.bank.aml.service.IngestionJobService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class DocumentIngestionConsumer {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionConsumer.class);

    private final IngestionJobService ingestionJobService;
    private final KafkaProcessedEventRepository processedEventRepository;
    private final MeterRegistry meterRegistry;

    @Autowired
    public DocumentIngestionConsumer(
        IngestionJobService ingestionJobService,
        KafkaProcessedEventRepository processedEventRepository,
        @Autowired(required = false) MeterRegistry meterRegistry
    ) {
        this.ingestionJobService = ingestionJobService;
        this.processedEventRepository = processedEventRepository;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(
        topics = KafkaTopics.INGESTION_REQUESTED,
        groupId = KafkaTopics.INGESTION_CONSUMER_GROUP,
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeIngestionRequest(
        @Payload DocumentEvent event,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("Received Kafka ingestion event: id={}, docId={}, topic={}, partition={}, offset={}",
            event.getEventId(), event.getDocumentId(), topic, partition, offset);

        recordMetric("kafka.consumer.messages.received", "topic", topic);

        // 1. Idempotency & Deduplication Check
        if (event.getEventId() != null && processedEventRepository.existsByEventId(event.getEventId())) {
            log.warn("Duplicate Kafka event detected and discarded: eventId={}, docId={}",
                event.getEventId(), event.getDocumentId());
            recordMetric("kafka.consumer.duplicates.discarded", "topic", topic);
            return;
        }

        // Set tenant context for the async processing thread
        String previousTenant = TenantContext.getTenantId();
        try {
            if (event.getTenantId() != null) {
                TenantContext.setTenantId(event.getTenantId());
            }

            // 2. Execute Document Ingestion and Indexing
            ingestionJobService.executeJob(event.getJobId());

            // 3. Persist processed event token for idempotency
            if (event.getEventId() != null) {
                processedEventRepository.save(new KafkaProcessedEvent(
                    event.getEventId(),
                    topic,
                    event.getTenantId(),
                    KafkaTopics.INGESTION_CONSUMER_GROUP
                ));
            }

            recordMetric("kafka.consumer.messages.processed", "topic", topic);
            log.info("Successfully processed Kafka ingestion event for document: {}", event.getDocumentId());

        } catch (Exception ex) {
            log.error("Error processing Kafka ingestion event {}: {}", event.getEventId(), ex.getMessage(), ex);
            recordMetric("kafka.consumer.messages.failed", "topic", topic);
            throw ex; // Re-throw to activate Kafka retry / DLT policy
        } finally {
            if (previousTenant != null) {
                TenantContext.setTenantId(previousTenant);
            } else {
                TenantContext.clear();
            }
        }
    }

    @KafkaListener(
        topics = KafkaTopics.INDEXING_DLT,
        groupId = "aml-document-indexing-dlt-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeDeadLetterQueue(
        @Payload DocumentEvent event,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic
    ) {
        log.error("TERMINAL INGESTION FAILURE received in DLT: eventId={}, docId={}, tenant={}, error={}",
            event.getEventId(), event.getDocumentId(), event.getTenantId(), event.getErrorMessage());

        recordMetric("kafka.ingestion.dlt.received", "tenant", event.getTenantId());
    }

    private void recordMetric(String name, String... tags) {
        if (meterRegistry != null) {
            try {
                Counter.builder(name).tags(tags).register(meterRegistry).increment();
            } catch (Exception ignored) {}
        }
    }
}
