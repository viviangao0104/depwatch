package com.depwatch.scan.scan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScanFindingRepository extends JpaRepository<ScanFinding, Long> {
    List<ScanFinding> findByScanId(UUID scanId);
}
