package com.depwatch.common.events;

/**
  * Kafka topic names in one place so services don't hand-type (and mistype) strings.
 */
public final class Topics {
    public static final String SCAN_REQUESTED = "scan.requested";
    public static final String SCAN_PROGRESS = "scan.progress";
    public static final String SCAN_COMPLETED = "scan.completed";
    public static final String VULN_DETECTED = "vuln.detected";

    private Topics() {
    }
}
