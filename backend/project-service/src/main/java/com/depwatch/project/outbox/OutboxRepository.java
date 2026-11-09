package com.depwatch.project.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * SELECT ... FOR UPDATE SKIP LOCKED：
     * 多个 project-service 实例同时轮询时，各自拿到不同的行，互不阻塞也不重复发送。
     * Hibernate 对 Postgres 会把 PESSIMISTIC_WRITE + SKIP_LOCKED 翻译成这条 SQL。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")) // -2 = SKIP LOCKED
    @Query("select e from OutboxEvent e where e.publishedAt is null and e.attempts < :maxAttempts order by e.createdAt")
    List<OutboxEvent> lockNextBatch(int maxAttempts, Pageable pageable);
}
