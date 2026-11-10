package com.bank.aml.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${app.kafka.partitions:3}")
    private int partitions;

    @Value("${app.kafka.replication-factor:1}")
    private short replicationFactor;

    @Bean
    public NewTopic documentUploadedTopic() {
        return TopicBuilder.name(KafkaTopics.DOCUMENT_UPLOADED)
            .partitions(partitions)
            .replicas(replicationFactor)
            .build();
    }

    @Bean
    public NewTopic documentIngestionTopic() {
        return TopicBuilder.name(KafkaTopics.INGESTION_REQUESTED)
            .partitions(partitions)
            .replicas(replicationFactor)
            .build();
    }

    @Bean
    public NewTopic documentIndexingCompletedTopic() {
        return TopicBuilder.name(KafkaTopics.INDEXING_COMPLETED)
            .partitions(partitions)
            .replicas(replicationFactor)
            .build();
    }

    @Bean
    public NewTopic documentIndexingFailedTopic() {
        return TopicBuilder.name(KafkaTopics.INDEXING_FAILED)
            .partitions(partitions)
            .replicas(replicationFactor)
            .build();
    }

    @Bean
    public NewTopic documentIndexingDltTopic() {
        return TopicBuilder.name(KafkaTopics.INDEXING_DLT)
            .partitions(partitions)
            .replicas(replicationFactor)
            .build();
    }

    @Bean
    public ProducerFactory<String, DocumentEvent> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        config.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, DocumentEvent> kafkaTemplate(ProducerFactory<String, DocumentEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ConsumerFactory<String, DocumentEvent> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, KafkaTopics.INGESTION_CONSUMER_GROUP);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);

        JsonDeserializer<DocumentEvent> jsonDeserializer = new JsonDeserializer<>(DocumentEvent.class);
        jsonDeserializer.addTrustedPackages("com.bank.aml.*");
        jsonDeserializer.setUseTypeHeaders(false);

        ErrorHandlingDeserializer<DocumentEvent> errorHandlingDeserializer =
            new ErrorHandlingDeserializer<>(jsonDeserializer);

        return new DefaultKafkaConsumerFactory<>(
            props,
            new StringDeserializer(),
            errorHandlingDeserializer
        );
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, DocumentEvent> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
            (record, ex) -> {
                log.error("Routing failed message to DLT topic '{}': key={}, error={}",
                    KafkaTopics.INDEXING_DLT, record.key(), ex.getMessage());
                return new org.apache.kafka.common.TopicPartition(KafkaTopics.INDEXING_DLT, record.partition());
            });

        // Exponential backoff: initial 500ms, multiplier 2.0, max 3 attempts
        ExponentialBackOff backOff = new ExponentialBackOff(500L, 2.0);
        backOff.setMaxAttempts(3);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, DocumentEvent> kafkaListenerContainerFactory(
        ConsumerFactory<String, DocumentEvent> consumerFactory,
        CommonErrorHandler kafkaErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, DocumentEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.setConcurrency(2);
        return factory;
    }
}
