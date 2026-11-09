package com.depwatch.common.events;

import java.time.Instant;
import java.util.UUID;

public record ScanCompletedEvent(
        UUID eventId,
        UUID scanId,
        UUID projectId,
        String projectName,
        UUID userId,
        String status,            // COMPLETED / FAILED
        String trigger,           // MANUAL / SCHEDULED
        int totalPackages,
        int vulnerablePackages,
        int critical,
        int high,
        int medium,
        int low,
        int newFindings,
        int resolvedFindings,
        String errorMessage,
        Instant occurredAt
) {
}
