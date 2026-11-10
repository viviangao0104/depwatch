# DepWatch — Architecture

> Open-source dependency vulnerability tracker: upload `package-lock.json` / `pom.xml` / `mvn dependency:tree` output,
> the system parses the full dependency graph, matches it against [OSV](https://osv.dev), keeps monitoring daily, and pushes new vulnerabilities in real time.

[中文版](ARCHITECTURE.zh-CN.md)

---

## 1. System overview

```
                        ┌─────────────────────────────────────────────────────────────┐
                        │                         AWS                                 │
  Browser ──HTTPS──▶ CloudFront ──▶ S3 (React static site)                            │
     │                                                                                │
     └──/api, /ws──▶ ALB ──▶ ┌──────────────┐                                        │
                             │   gateway    │  Spring Cloud Gateway                   │
                             │  :8080       │  · verifies JWT → injects X-User-Id     │
                             └──┬───┬───┬───┘  · Redis token-bucket rate limit · CORS · WS proxy
                 ┌──────────────┘   │   └──────────────┐                              │
                 ▼                  ▼                  ▼                              │
        ┌────────────────┐ ┌────────────────┐ ┌────────────────┐                      │
        │project-service │ │  scan-service  │ │ alert-service  │                      │
        │ :8081          │ │  :8082         │ │ :8083          │                      │
        │ users / JWT    │ │ manifest parse │ │ alert rules    │                      │
        │ project CRUD   │ │ dependency graph│ │ event dedup   │                      │
        │ manifest upload│ │ OSV batch query│ │ WebSocket push │                      │
        │ outbox         │ │ incremental diff│ │ webhook / email│                     │
        │                │ │ daily rescan   │ │                │                      │
        └───┬────────┬───┘ └──┬───────┬──┬──┘ └──────┬─────────┘                      │
            ▼        │        ▼       │  ▼           ▼                                │
     ┌───────────┐   │  ┌──────────┐  │ ┌──────────────┐                              │
     │ Postgres  │   │  │ Postgres │  │ │  Postgres    │   (RDS, database-per-service)│
     │ _project  │   │  │ _scan    │  │ │  _alert      │                              │
     └───────────┘   │  └──────────┘  │ └──────────────┘                              │
                     ▼                ▼                                               │
              ┌─────────────┐   ┌──────────┐    ┌──────────────────┐                  │
              │ S3 (manifest│   │  Redis   │    │  Kafka (MSK)     │                  │
              │  storage)   │   │cache/rate│    │  4 topics        │                  │
              └─────────────┘   └──────────┘    └──────────────────┘                  │
                                                        ▲                             │
                                              ┌─────────┴─────────┐                   │
                                              │  api.osv.dev      │  (external)       │
                                              └───────────────────┘                   │
                        └─────────────────────────────────────────────────────────────┘
```

Local development runs the same topology with Docker Compose: Postgres / Redis / Kafka (KRaft) / 4 Java services / Nginx frontend.

---

## 2. Core flows

### 2.1 Upload → scan → notify (main path)

```
 1. POST /api/projects/{id}/manifests  (multipart)
      project-service
        ├─ stores the file via ManifestStorage (local disk / S3)
        ├─ in ONE DB transaction: insert manifests + insert outbox_events(ScanRequested)
        └─ returns scanId immediately  ← frontend subscribes to /topic/scans/{scanId} right away

 2. OutboxPublisher (polls every second, SELECT ... FOR UPDATE SKIP LOCKED)
        └─ publishes to Kafka topic scan.requested   key = projectId

 3. scan-service consumes scan.requested
        ├─ idempotency: scans.id is the primary key; if it exists and isn't FAILED → ack and skip
        ├─ PARSING   → ManifestParser builds a DependencyGraph (nodes + directed edges)
        ├─ QUERYING  → OSV /v1/querybatch in batches of ≤ 1000
        │               a scan.progress event after every batch
        ├─ ENRICHING → GET /v1/vulns/{id} for each unique vulnerability
        │               Redis cache (24h) → miss → HTTP → persist to vulnerabilities table
        ├─ DIFFING   → compare with the project's previous scan, mark new / resolved
        ├─ persist scans / scan_dependencies / scan_edges / scan_findings
        └─ publish scan.completed + N × vuln.detected (new findings only)

 4. alert-service consumes three event types
        ├─ processed_events table deduplicates by eventId (at-least-once → exactly-once effect)
        ├─ scan.progress  → WebSocket  /topic/scans/{scanId}
        ├─ vuln.detected  → match the user's alert_rules (min severity) → persist alert
        │                   → WebSocket /topic/users/{userId}/alerts
        │                   → optional Slack-compatible webhook / email (SES)
        └─ scan.completed → summary alert
```

### 2.2 Scheduled rescan (continuous monitoring)

Every day at 03:00 the `RescanScheduler` in scan-service (ShedLock guarantees a single run across replicas)
takes each project's latest successful scan and **re-queries OSV with the stored package list — no manifest re-parse**.
OSV adds vulnerabilities every day; a package that was clean yesterday can have a CVE today. That is the whole point of "continuous tracking".
Only **new** findings produce `vuln.detected` events.

---

## 3. Key design decisions (interview material)

| Decision | Choice | Why / trade-off |
|---|---|---|
| Service-to-service auth | Gateway verifies the JWT, downstream trusts `X-User-Id` | Downstream services live in a private subnet reachable only through the gateway; avoids re-parsing the JWT in every service. The cost: downstream must never be exposed publicly (security groups / NetworkPolicy) and the gateway **must strip** client-supplied X-User-* headers |
| Service discovery | No Eureka — DNS | Docker Compose, ECS Service Connect and Kubernetes Services all provide DNS discovery. Eureka solves a problem from the pre-orchestrator era; adding it here would only add a single point of failure. Explaining this is worth more than "I used Eureka" |
| Reliable event publishing | Transactional Outbox | Calling `kafkaTemplate.send()` inside a business transaction leads to "DB committed but Kafka not" or the reverse. The outbox stores the event as business data in the same transaction, then a poller ships it: at-least-once |
| Consumer idempotency | Business key + processed_events table | Kafka is at-least-once; rebalances redeliver. scan-service is naturally idempotent on scanId; alert-service deduplicates by eventId |
| Large payloads | Events carry only a storage key | A `package-lock.json` is easily several MB; Kafka's default max message size is 1 MB. Claim-Check pattern via S3 / shared volume |
| Third-party API protection | Resilience4j RateLimiter + Retry + CircuitBreaker | OSV is a free public service — be polite; retry network blips with exponential backoff; fail fast when OSV is down instead of exhausting the thread pool |
| Vulnerability detail caching | Redis (24h TTL) + Postgres persistence | The same lodash CVE is hit by hundreds of projects — Redis absorbs the repeats; Postgres lets the UI browse history without OSV being online |
| Severity scoring | Prefer GHSA `database_specific.severity`, else compute CVSS 3.x base score | OSV's `severity` field contains only a CVSS vector string, no score — so the CVSS 3.1 formula is implemented in-house |
| Dependency graph storage | Node table + edge table, paths via on-demand BFS | A React project with 1,500 nodes / 3,000 edges is normal. Store the adjacency list; compute "why is this package here" (shortest path) when asked |
| Scheduling | ShedLock (JDBC) | With multiple replicas `@Scheduled` fires on each; ShedLock uses a DB row lock — far lighter than clustered Quartz |
| Real-time push | STOMP over WebSocket, proxied by the gateway | Browsers can't set custom headers on WebSocket; the JWT goes in `?token=` and the gateway verifies it during the handshake |
| Databases | database-per-service (3 databases on one RDS instance) | Clear service boundaries and independent migrations; one instance keeps local/budget deployments cheap |

---

## 4. Kafka topics and events

| Topic | Producer | Consumer | Key | Notes |
|---|---|---|---|---|
| `scan.requested` | project-service (outbox) | scan-service | projectId | scans for one project are processed in order |
| `scan.progress` | scan-service | alert-service | scanId | high-frequency, loss-tolerant (UI progress bar) |
| `scan.completed` | scan-service | alert-service | projectId | scan summary |
| `vuln.detected` | scan-service | alert-service | userId | one event per new finding |

Dead letters: every consumer uses `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`; after 3 retries the record goes to `<topic>.DLT`.

Event classes live in `backend/common/.../events/`; every event carries an `eventId` (UUID) and `occurredAt`.

---

## 5. Data model

### project-service (`depwatch_project`)
- `users`: id, email (unique), password_hash (BCrypt), display_name
- `projects`: id, owner_id, name, description — unique(owner_id, name)
- `manifests`: id, project_id, file_name, storage_key, manifest_type, size_bytes, scan_id
- `outbox_events`: id, aggregate_type, aggregate_id, event_type, topic, partition_key, payload (jsonb), published_at, attempts

### scan-service (`depwatch_scan`)
- `scans`: id (= scanId from the event), project_id, project_name, user_id, trigger (MANUAL/SCHEDULED), status, severity counts, started_at, completed_at, error
- `scan_dependencies`: scan_id, node_id ("npm:lodash@4.17.15"), ecosystem, name, version, direct, depth
- `scan_edges`: scan_id, parent_node_id, child_node_id
- `vulnerabilities`: id (OSV id), summary, details, severity, cvss_score, cvss_vector, aliases (jsonb), references (jsonb), published, modified, raw (jsonb)
- `scan_findings`: scan_id, node_id, vuln_id, fixed_version, is_new
- `shedlock`

### alert-service (`depwatch_alert`)
- `alerts`: id, user_id, project_id, scan_id, vuln_id, severity, title, message, read_at
- `alert_rules`: user_id (pk), min_severity, in_app, webhook_url, notify_on_scan_complete
- `processed_events`: event_id (pk), processed_at

---

## 6. Manifest parsers

`ManifestParser` interface + three implementations, selected by file name; adding `requirements.txt` / `go.sum` is one new class:

| Parser | Input | Transitive | Key points |
|---|---|---|---|
| `NpmLockfileParser` | `package-lock.json` v1/v2/v3 | ✅ full | `packages` is a flat map keyed by install path (`node_modules/a/node_modules/b`). Reconstructing edges means simulating Node's module resolution: walk up from the current path looking for `node_modules/<name>` |
| `MavenTreeParser` | `mvn dependency:tree` text | ✅ full | Indentation (3 chars per level: `+- ` / `\- ` / `|  `) + a stack rebuilds the tree |
| `MavenPomParser` | `pom.xml` | ❌ direct only | Resolves `${property}` placeholders and `<dependencyManagement>` versions; XXE-hardened. True transitive resolution would require downloading every parent POM, so users are pointed to dependency:tree |

All produce a `DependencyGraph { root, nodes: Map<nodeId, Node>, edges: Map<parent, Set<child>> }`.

---

## 7. Module layout

```
depwatch/
├── backend/
│   ├── pom.xml                 parent POM: versions, dependency management
│   ├── common/                 events, Severity, UserContext filter, ManifestStorage (Local/S3), error handling
│   ├── gateway/                Spring Cloud Gateway (WebFlux)
│   ├── project-service/
│   ├── scan-service/
│   ├── alert-service/
│   └── Dockerfile              multi-stage build, ARG MODULE reused by all services
├── frontend/                   Vite + React + TypeScript
├── infra/
│   ├── docker/                 Postgres init script, Nginx config
│   └── aws/                    Terraform (budget EC2 option) + ECS task-definition templates
├── scripts/                    check-env.sh / dev.sh / sample manifests
├── docker-compose.yml
├── .github/workflows/ci.yml
└── docs/
```

---

## 8. Where to take it next

1. **SBOM support**: parse CycloneDX / SPDX (industry standard).
2. **License compliance**: pull licenses from the npm registry / Maven Central, flag GPL contamination.
3. **GitHub App**: install on a repo, pull lockfiles automatically, comment on PRs that introduce vulnerabilities.
4. **OpenTelemetry**: one upload crosses 3 services + Kafka; a distributed trace makes that visible.
5. **Kubernetes**: Helm chart + HPA; scale scan-service on Kafka lag with KEDA.
