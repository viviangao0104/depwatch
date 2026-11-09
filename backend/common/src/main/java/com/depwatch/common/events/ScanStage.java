package com.depwatch.common.events;

public enum ScanStage {
    QUEUED,
    PARSING,
    QUERYING,
    ENRICHING,
    DIFFING,
    COMPLETED,
    FAILED
}
