package com.depwatch.scan.scan;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScanRepository extends JpaRepository<Scan, UUID> {

    Page<Scan> findByProjectIdAndUserIdOrderByStartedAtDesc(UUID projectId, UUID userId, Pageable pageable);

    Page<Scan> findByUserIdOrderByStartedAtDesc(UUID userId, Pageable pageable);

    Optional<Scan> findByIdAndUserId(UUID id, UUID userId);

    /** The project's previous successful scan (for diffing), excluding the current one. */
    Optional<Scan> findFirstByProjectIdAndStatusAndIdNotOrderByStartedAtDesc(UUID projectId, Scan.Status status, UUID excludeId);

    /** Latest successful scan per project — the input for scheduled rescans. */
    @Query(value = """
            SELECT DISTINCT ON (project_id) *
            FROM scans
            WHERE status = 'COMPLETED'
            ORDER BY project_id, started_at DESC
            """, nativeQuery = true)
    List<Scan> findLatestCompletedPerProject();
}
