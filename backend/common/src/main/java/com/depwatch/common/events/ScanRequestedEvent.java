package com.depwatch.common.events;

import java.time.Instant;
import java.util.UUID;

/**
 * project-service → scan-service。
  * Carries only the storage key (Claim Check pattern), never the file contents.
 */
public record ScanRequestedEvent(
        UUID eventId,
        UUID scanId,
        UUID projectId,
        String projectName,
        UUID userId,
        String manifestKey,
        String manifestFileName,
        Instant occurredAt
) {
    public static ScanRequestedEvent of(UUID scanId, UUID projectId, String projectName, UUID userId,
                                        String manifestKey, String manifestFileName) {
        return new ScanRequestedEvent(UUID.randomUUID(), scanId, projectId, projectName, userId,
                manifestKey, manifestFileName, Instant.now());
    }
}
