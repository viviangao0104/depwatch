CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE projects (
    id          UUID PRIMARY KEY,
    owner_id    UUID         NOT NULL REFERENCES users (id),
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_projects_owner_name UNIQUE (owner_id, name)
);
CREATE INDEX idx_projects_owner ON projects (owner_id);

CREATE TABLE manifests (
    id            UUID PRIMARY KEY,
    project_id    UUID         NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    file_name     VARCHAR(255) NOT NULL,
    storage_key   VARCHAR(512) NOT NULL,
    manifest_type VARCHAR(40)  NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    scan_id       UUID         NOT NULL,
    uploaded_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_manifests_project ON manifests (project_id, uploaded_at DESC);

-- Transactional Outbox: events are written in the same transaction as business data and shipped to Kafka by a background job
CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(60)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(255) NOT NULL,       -- fully qualified event class name, used for deserialization
    topic          VARCHAR(100) NOT NULL,
    partition_key  VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    attempts       INT          NOT NULL DEFAULT 0,
    last_error     VARCHAR(1000)
);
-- Index only unpublished rows (partial index) — pays off more as the table grows
CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
