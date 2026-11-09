CREATE TABLE scans (
    id                  UUID PRIMARY KEY,                 -- 来自事件的 scanId，天然幂等键
    project_id          UUID         NOT NULL,
    project_name        VARCHAR(120) NOT NULL,
    user_id             UUID         NOT NULL,
    manifest_key        VARCHAR(512),
    manifest_file_name  VARCHAR(255),
    trigger_type        VARCHAR(20)  NOT NULL,            -- MANUAL / SCHEDULED
    status              VARCHAR(20)  NOT NULL,            -- RUNNING / COMPLETED / FAILED
    total_packages      INT          NOT NULL DEFAULT 0,
    direct_packages     INT          NOT NULL DEFAULT 0,
    vulnerable_packages INT          NOT NULL DEFAULT 0,
    critical_count      INT          NOT NULL DEFAULT 0,
    high_count          INT          NOT NULL DEFAULT 0,
    medium_count        INT          NOT NULL DEFAULT 0,
    low_count           INT          NOT NULL DEFAULT 0,
    unknown_count       INT          NOT NULL DEFAULT 0,
    new_findings        INT          NOT NULL DEFAULT 0,
    resolved_findings   INT          NOT NULL DEFAULT 0,
    error_message       VARCHAR(2000),
    started_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ
);
CREATE INDEX idx_scans_project ON scans (project_id, started_at DESC);
CREATE INDEX idx_scans_user ON scans (user_id, started_at DESC);

-- 依赖图节点。node_id = "<ecosystem>:<name>@<version>"
CREATE TABLE scan_dependencies (
    id        BIGSERIAL PRIMARY KEY,
    scan_id   UUID         NOT NULL REFERENCES scans (id) ON DELETE CASCADE,
    node_id   VARCHAR(512) NOT NULL,
    ecosystem VARCHAR(30)  NOT NULL,
    name      VARCHAR(400) NOT NULL,
    version   VARCHAR(100) NOT NULL,
    direct    BOOLEAN      NOT NULL,
    depth     INT          NOT NULL,
    CONSTRAINT uq_scan_dep UNIQUE (scan_id, node_id)
);

-- 依赖图边（邻接表）。路径按需 BFS。
CREATE TABLE scan_edges (
    scan_id        UUID         NOT NULL REFERENCES scans (id) ON DELETE CASCADE,
    parent_node_id VARCHAR(512) NOT NULL,
    child_node_id  VARCHAR(512) NOT NULL,
    PRIMARY KEY (scan_id, parent_node_id, child_node_id)
);
CREATE INDEX idx_scan_edges_child ON scan_edges (scan_id, child_node_id);

-- OSV 漏洞详情的本地副本
CREATE TABLE vulnerabilities (
    id           VARCHAR(60) PRIMARY KEY,                 -- OSV id: GHSA-xxxx / CVE-xxxx / ...
    summary      VARCHAR(1000),
    details      TEXT,
    severity     VARCHAR(10)  NOT NULL,
    cvss_score   NUMERIC(3, 1),
    cvss_vector  VARCHAR(200),
    aliases      JSONB        NOT NULL DEFAULT '[]',
    refs         JSONB        NOT NULL DEFAULT '[]',
    published_at TIMESTAMPTZ,
    modified_at  TIMESTAMPTZ,
    raw          JSONB        NOT NULL,
    fetched_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE scan_findings (
    id            BIGSERIAL PRIMARY KEY,
    scan_id       UUID         NOT NULL REFERENCES scans (id) ON DELETE CASCADE,
    node_id       VARCHAR(512) NOT NULL,
    vuln_id       VARCHAR(60)  NOT NULL REFERENCES vulnerabilities (id),
    fixed_version VARCHAR(100),
    is_new        BOOLEAN      NOT NULL DEFAULT false,
    CONSTRAINT uq_scan_finding UNIQUE (scan_id, node_id, vuln_id)
);
CREATE INDEX idx_findings_scan ON scan_findings (scan_id);

-- ShedLock 分布式锁表
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
