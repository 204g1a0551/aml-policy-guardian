package com.bank.aml.repository;

import com.bank.aml.entity.KafkaProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KafkaProcessedEventRepository extends JpaRepository<KafkaProcessedEvent, String> {

    boolean existsByEventId(String eventId);

    Optional<KafkaProcessedEvent> findByEventIdAndConsumerGroup(String eventId, String consumerGroup);
}
