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
  * Consumer idempotency. "insert into processed_events" and "the writes caused by handling the event" share one transaction:
  * commit → the event is handled and marked; rollback → neither happened, so the retry gets in again.
 *
  * Why only existsById, with no handling of concurrent insert conflicts:
  * Kafka guarantees a single consumer instance per partition at any time, and redeliveries always land on the
  * same partition (replay after a rebalance), so one eventId is never processed by two threads at once.
  * Even if a PK collision did happen, the rollback + Kafka retry makes existsById return true — still correct.
 */
@Component
public class IdempotencyGuard {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyGuard.class);

    private final ProcessedEventRepository repo;

    public IdempotencyGuard(ProcessedEventRepository repo) {
        this.repo = repo;
    }

    /**
      * @return true on first processing (now recorded), false for a duplicate (caller should skip)
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

    /** Nightly cleanup of records older than 7 days; events past Kafka's retention can't be replayed anyway. */
    @Scheduled(cron = "0 30 4 * * *")
    @Transactional
    public void cleanup() {
        int n = repo.deleteOlderThan(Instant.now().minus(Duration.ofDays(7)));
        if (n > 0) log.info("Cleaned {} processed_events older than 7 days", n);
    }
}
