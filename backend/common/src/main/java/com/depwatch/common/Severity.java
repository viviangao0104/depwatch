package com.depwatch.common;

import java.util.Locale;

/**
  * Unified vulnerability severity. Declaration order is severity order, so ordinal / compareTo can implement "minimum alert level" filtering.
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
      * GHSA / OSV database_specific.severity uses CRITICAL / HIGH / MODERATE / LOW.
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
      * CVSS v3.x base score → qualitative rating (official mapping).
     */
    public static Severity fromCvssScore(double score) {
        if (score <= 0.0) return UNKNOWN;   // 0.0 = None
        if (score < 4.0) return LOW;
        if (score < 7.0) return MEDIUM;
        if (score < 9.0) return HIGH;
        return CRITICAL;
    }
}
