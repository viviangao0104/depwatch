package com.depwatch.alert.alert;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {
    Page<Alert> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Alert> findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(UUID userId);

    Optional<Alert> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("update Alert a set a.readAt = :now where a.userId = :userId and a.readAt is null")
    int markAllRead(UUID userId, Instant now);
}
