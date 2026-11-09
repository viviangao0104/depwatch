CREATE TABLE alerts (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL,
    project_id  UUID         NOT NULL,
    project_name VARCHAR(120),
    scan_id     UUID         NOT NULL,
    vuln_id     VARCHAR(60),
    severity    VARCHAR(10)  NOT NULL,
    kind        VARCHAR(30)  NOT NULL,       -- NEW_VULNERABILITY / SCAN_COMPLETED / SCAN_FAILED
    title       VARCHAR(300) NOT NULL,
    message     VARCHAR(2000),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    read_at     TIMESTAMPTZ
);
CREATE INDEX idx_alerts_user_unread ON alerts (user_id, created_at DESC) WHERE read_at IS NULL;
CREATE INDEX idx_alerts_user ON alerts (user_id, created_at DESC);

CREATE TABLE alert_rules (
    user_id       UUID PRIMARY KEY,
    min_severity  VARCHAR(10) NOT NULL DEFAULT 'LOW',
    in_app        BOOLEAN     NOT NULL DEFAULT true,
    webhook_url   VARCHAR(1000),
    notify_on_scan_complete BOOLEAN NOT NULL DEFAULT true,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 消费幂等：Kafka at-least-once，重复投递的事件在这里被识别并跳过
CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    event_type   VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- 定期清理（> 7 天）即可，见 ProcessedEventCleanup
CREATE INDEX idx_processed_events_time ON processed_events (processed_at);
