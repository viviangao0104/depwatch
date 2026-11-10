# DepWatch

**Open-source dependency vulnerability tracker.** Upload a `package-lock.json`, `pom.xml`, or `mvn dependency:tree` output; DepWatch parses the full dependency graph, matches every package against the [OSV](https://osv.dev) vulnerability database, re-scans daily, and pushes new findings in real time.

Java 21 · Spring Boot 3.3 · Spring Cloud Gateway · Kafka · PostgreSQL · Redis · React 18 · Docker · AWS (Terraform)

[中文说明](README.zh-CN.md)

```
Browser ─▶ gateway (JWT, rate limiting, WebSocket proxy)
             ├─▶ project-service  users / projects / manifest upload / transactional outbox
             ├─▶ scan-service     dependency graph → OSV batch query → CVSS scoring → diff → daily rescan
             └─▶ alert-service    idempotent consumers → alert rules → WebSocket / Slack webhook
                        │ Kafka: scan.requested · scan.progress · scan.completed · vuln.detected
```

Full design: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

---

## Quick start

### 0. Prerequisites

Java 21, Maven 3.9+, Node 20+, Docker Desktop (≥ 6 GB RAM). New Mac? See [docs/SETUP_MAC.md](docs/SETUP_MAC.md). Then:

```bash
scripts/check-env.sh
```

### 1. Run everything in Docker

```bash
docker compose up -d --build        # first run: ~5–8 min (dependency download + image build)
```

| URL | What |
|---|---|
| http://localhost:3000 | Frontend |
| http://localhost:8080 | Gateway (API entry point) |
| http://localhost:8090 | Kafka UI — topics and messages |
| http://localhost:8081/swagger-ui.html | project-service API docs (8082 / 8083 for the others) |

### 2. Seed a demo

```bash
scripts/dev.sh demo
```

Registers a user, creates two projects, and uploads the sample manifests in `scripts/samples/` (old versions with known CVEs). Log in with the printed credentials and watch the scans run live.

### 3. Develop in an IDE

```bash
scripts/dev.sh infra                        # Postgres / Redis / Kafka only
cd backend && mvn -q install -DskipTests    # install the common module locally
```

Run each `*Application` class from IntelliJ; they default to localhost infrastructure. Frontend:

```bash
cd frontend && npm install && npm run dev   # http://localhost:5173, proxied to the gateway
```

### 4. Tests

```bash
cd backend && mvn test
```

Unit tests cover the three manifest parsers, the CVSS 3.1 calculator and the version comparator — the pieces where correctness matters most.

---

## Project layout

```
backend/
  common/            events, Severity, user-context filter, ManifestStorage (local/S3), error handling
  gateway/           Spring Cloud Gateway: JwtAuthGlobalFilter, Redis rate limiter, routes
  project-service/   auth/  project/  outbox/
  scan-service/      parser/ (npm · maven)  graph/  osv/ (Resilience4j)  vuln/ (CVSS)  scan/ (pipeline · scheduler)
  alert-service/     alert/  idempotency/  ws/ (STOMP)  notify/ (webhook)
frontend/            Vite + React + TS: auth, projects, live progress, findings, dependency paths, alerts
infra/
  docker/            Postgres multi-database init
  aws/terraform/     Option A: EC2 + S3 + CloudFront + ECR + IAM + GitHub OIDC
  aws/ecs/           Option B: Fargate task-definition templates + notes
scripts/             check-env.sh · dev.sh · deploy-ec2.sh · samples/
docs/                ARCHITECTURE · SETUP_MAC · AWS_DEPLOY · INTERVIEW_NOTES
.github/workflows/   CI (test + build) / CD (ECR → EC2 via SSM, S3 → CloudFront)
```

---

## Deploying to AWS

See [docs/AWS_DEPLOY.md](docs/AWS_DEPLOY.md). Budget option (≈ $15/month):

```bash
cd infra/aws/terraform && cp terraform.tfvars.example terraform.tfvars   # fill in
terraform init && terraform apply
cd ../../.. && scripts/deploy-ec2.sh ~/.ssh/your-key.pem
```

Set the repository variable `DEPLOY_ENABLED=true` to turn on the CD job.

---

## Supported manifests

| File | Transitive deps | How to get it |
|---|---|---|
| `package-lock.json` (v1/v2/v3) | ✅ | already in your project |
| `dependency-tree.txt` | ✅ | `mvn dependency:tree -DoutputFile=dependency-tree.txt` |
| `pom.xml` | direct only | already in your project |

## License

MIT
