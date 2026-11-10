package com.bank.aml.kafka;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class DocumentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DocumentEventPublisher.class);

    private final KafkaTemplate<String, DocumentEvent> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Autowired
    public DocumentEventPublisher(
        @Autowired(required = false) KafkaTemplate<String, DocumentEvent> kafkaTemplate,
        @Autowired(required = false) MeterRegistry meterRegistry
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }

    public boolean isKafkaEnabled() {
        return kafkaTemplate != null;
    }

    public CompletableFuture<Void> publishEvent(String topic, DocumentEvent event) {
        recordMetric("kafka.events.published", "type", event.getEventType(), "topic", topic);

        if (kafkaTemplate == null) {
            log.info("[KAFKA-FALLBACK] Simulated publishing event '{}' to topic '{}' for documentId={}",
                event.getEventType(), topic, event.getDocumentId());
            return CompletableFuture.completedFuture(null);
        }

        try {
            // Partition by tenantId to guarantee in-order delivery per tenant
            String partitionKey = event.getTenantId() != null ? event.getTenantId() : "default-bank";
            return kafkaTemplate.send(topic, partitionKey, event)
                .thenAccept(result -> log.debug("Published event '{}' to topic '{}' [partition={}, offset={}]",
                    event.getEventType(), topic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset()))
                .exceptionally(ex -> {
                    log.error("Failed to publish event '{}' to topic '{}': {}",
                        event.getEventType(), topic, ex.getMessage(), ex);
                    recordMetric("kafka.events.published.failure", "type", event.getEventType(), "topic", topic);
                    return null;
                });
        } catch (Exception ex) {
            log.error("Error initiating Kafka send for topic '{}': {}", topic, ex.getMessage(), ex);
            recordMetric("kafka.events.published.failure", "type", event.getEventType(), "topic", topic);
            return CompletableFuture.failedFuture(ex);
        }
    }

    private void recordMetric(String name, String... tags) {
        if (meterRegistry != null) {
            try {
                Counter.builder(name)
                    .tags(tags)
                    .register(meterRegistry)
                    .increment();
            } catch (Exception e) {
                // Ignore metric recording errors
            }
        }
    }
}
