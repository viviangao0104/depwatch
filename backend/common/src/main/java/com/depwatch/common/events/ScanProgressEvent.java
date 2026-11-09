package com.depwatch.common.events;

import java.time.Instant;
import java.util.UUID;

/**
 * scan-service → alert-service → WebSocket。高频、允许丢失。
 */
public record ScanProgressEvent(
        UUID eventId,
        UUID scanId,
        UUID projectId,
        UUID userId,
        ScanStage stage,
        int current,
        int total,
        String message,
        Instant occurredAt
) {
    public static ScanProgressEvent of(UUID scanId, UUID projectId, UUID userId,
                                       ScanStage stage, int current, int total, String message) {
        return new ScanProgressEvent(UUID.randomUUID(), scanId, projectId, userId,
                stage, current, total, message, Instant.now());
    }
}
