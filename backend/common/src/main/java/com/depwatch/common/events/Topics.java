package com.depwatch.common.events;

/**
 * Kafka topic 名称集中定义，避免各服务手写字符串拼错。
 */
public final class Topics {
    public static final String SCAN_REQUESTED = "scan.requested";
    public static final String SCAN_PROGRESS = "scan.progress";
    public static final String SCAN_COMPLETED = "scan.completed";
    public static final String VULN_DETECTED = "vuln.detected";

    private Topics() {
    }
}
