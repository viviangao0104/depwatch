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
 * 轮询 outbox，把事件搬到 Kafka。
 *
 * 保证：at-least-once。极端情况（Kafka ack 了但 DB 更新 published_at 失败）会重发，
 * 所以消费端必须幂等——scan-service 用 scanId 主键，alert-service 用 eventId 去重表。
 *
 * 进阶替代：Debezium 读 Postgres WAL 做 CDC，零轮询、更低延迟，但多一套组件。
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
                // 反序列化成真实事件类型再发送，这样 JsonSerializer 会写入 __TypeId__ 头，消费端能直接拿到类型
                Class<?> type = Class.forName(e.getEventType());
                Object event = mapper.readValue(e.getPayload(), type);
                // 同步等待 ack（acks=all），确保 Kafka 真的收到了才标记已发布
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
