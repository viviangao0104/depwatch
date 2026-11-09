package com.depwatch.common.events;

import java.time.Instant;
import java.util.UUID;

/**
 * project-service → scan-service。
 * 只携带 storage key（Claim Check 模式），不带文件内容。
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
