package com.depwatch.common;

import java.util.Locale;

/**
 * 统一的漏洞严重度。顺序即严重程度，便于用 ordinal / compareTo 做"最低告警级别"过滤。
 */
public enum Severity {
    UNKNOWN(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CRITICAL(4);

    private final int rank;

    Severity(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public boolean atLeast(Severity other) {
        return this.rank >= other.rank;
    }

    /**
     * GHSA / OSV database_specific.severity 用的是 CRITICAL / HIGH / MODERATE / LOW。
     */
    public static Severity fromLabel(String label) {
        if (label == null) return UNKNOWN;
        return switch (label.trim().toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> CRITICAL;
            case "HIGH" -> HIGH;
            case "MODERATE", "MEDIUM" -> MEDIUM;
            case "LOW" -> LOW;
            default -> UNKNOWN;
        };
    }

    /**
     * CVSS v3.x 基础分 → 定性等级（官方映射表）。
     */
    public static Severity fromCvssScore(double score) {
        if (score <= 0.0) return UNKNOWN;   // 0.0 = None
        if (score < 4.0) return LOW;
        if (score < 7.0) return MEDIUM;
        if (score < 9.0) return HIGH;
        return CRITICAL;
    }
}
