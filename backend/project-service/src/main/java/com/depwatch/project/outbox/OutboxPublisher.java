package com.depwatch.project.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
  * Polls the outbox and ships events to Kafka.
 *
  * Guarantee: at-least-once. In the edge case where Kafka acks but updating published_at fails, the event is re-sent,
  * so consumers must be idempotent — scan-service via the scanId primary key, alert-service via an eventId dedup table.
 *
  * Advanced alternative: Debezium CDC on the Postgres WAL — no polling, lower latency, one more component.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository repo;
    private final KafkaTemplate<String, Object> kafka;
    private final ObjectMapper mapper;
    private final int batchSize;
    private final int maxAttempts;

    public OutboxPublisher(OutboxRepository repo,
                           KafkaTemplate<String, Object> kafka,
                           ObjectMapper mapper,
                           @Value("${depwatch.outbox.batch-size:50}") int batchSize,
                           @Value("${depwatch.outbox.max-attempts:10}") int maxAttempts) {
        this.repo = repo;
        this.kafka = kafka;
        this.mapper = mapper;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${depwatch.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = repo.lockNextBatch(maxAttempts, PageRequest.of(0, batchSize));
        if (batch.isEmpty()) return;

        for (OutboxEvent e : batch) {
            try {
                // Deserialize into the real event type before sending so JsonSerializer writes the __TypeId__ header and consumers get the type
                Class<?> type = Class.forName(e.getEventType());
                Object event = mapper.readValue(e.getPayload(), type);
                // Wait synchronously for the ack (acks=all) so we only mark published once Kafka really has it
                kafka.send(e.getTopic(), e.getPartitionKey(), event).get(10, TimeUnit.SECONDS);
                e.markPublished();
            } catch (Exception ex) {
                e.markFailed(ex.getMessage());
                log.warn("Outbox publish failed (attempt {}/{}) for event {}: {}",
                        e.getAttempts(), maxAttempts, e.getId(), ex.getMessage());
            }
        }
        log.debug("Outbox batch processed: {} events", batch.size());
    }
}
