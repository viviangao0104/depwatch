package com.depwatch.alert.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * 消费幂等。把"插入 processed_events"和"处理事件产生的写入"放在同一个事务里：
 * 事务提交 → 事件被处理且被标记；事务回滚 → 两者都没发生，下次重试还能进来。
 *
 * 为什么只查 existsById 而不处理并发插入冲突：
 * Kafka 保证同一分区同一时刻只有一个消费者实例在消费，而重复投递总是发生在同一分区上
 * （rebalance 后重放），所以同一个 eventId 不会被两个线程同时处理。
 * 即便极端情况撞了主键，事务回滚 + Kafka 重试后 existsById 会返回 true，结果仍然正确。
 */
@Component
public class IdempotencyGuard {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyGuard.class);

    private final ProcessedEventRepository repo;

    public IdempotencyGuard(ProcessedEventRepository repo) {
        this.repo = repo;
    }

    /**
     * @return true 表示首次处理（已记录），false 表示重复事件（调用方应直接跳过）
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markProcessed(UUID eventId, String eventType) {
        if (repo.existsById(eventId)) {
            log.info("Duplicate event {} ({}) ignored", eventId, eventType);
            return false;
        }
        repo.save(new ProcessedEvent(eventId, eventType));
        return true;
    }

    /** 每天凌晨清理 7 天前的记录；超过 Kafka 保留期的事件不可能再被重放。 */
    @Scheduled(cron = "0 30 4 * * *")
    @Transactional
    public void cleanup() {
        int n = repo.deleteOlderThan(Instant.now().minus(Duration.ofDays(7)));
        if (n > 0) log.info("Cleaned {} processed_events older than 7 days", n);
    }
}
