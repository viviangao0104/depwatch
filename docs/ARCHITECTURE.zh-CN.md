# DepWatch 架构设计

> 开源依赖漏洞追踪平台：上传 `package-lock.json` / `pom.xml` / `mvn dependency:tree` 输出，
> 系统解析完整依赖图，比对 [OSV](https://osv.dev) 漏洞数据库，持续监控并实时推送新漏洞。

---

## 1. 整体架构

```
                        ┌─────────────────────────────────────────────────────────────┐
                        │                         AWS                                 │
  Browser ──HTTPS──▶ CloudFront ──▶ S3 (React 静态站点)                                │
     │                                                                                │
     └──/api, /ws──▶ ALB ──▶ ┌──────────────┐                                        │
                             │   gateway    │  Spring Cloud Gateway                   │
                             │  :8080       │  · JWT 校验 → 注入 X-User-Id            │
                             └──┬───┬───┬───┘  · Redis 令牌桶限流  · CORS · WS 代理    │
                 ┌──────────────┘   │   └──────────────┐                              │
                 ▼                  ▼                  ▼                              │
        ┌────────────────┐ ┌────────────────┐ ┌────────────────┐                      │
        │project-service │ │  scan-service  │ │ alert-service  │                      │
        │ :8081          │ │  :8082         │ │ :8083          │                      │
        │ 用户/JWT 签发   │ │ manifest 解析   │ │ 告警规则        │                      │
        │ 项目 CRUD       │ │ 依赖图构建      │ │ 事件去重        │                      │
        │ manifest 上传   │ │ OSV 批量查询    │ │ WebSocket 推送  │                      │
        │ Outbox 发件箱   │ │ 增量 diff       │ │ Webhook/Email   │                      │
        │                │ │ 定时重扫        │ │                │                      │
        └───┬────────┬───┘ └──┬───────┬──┬──┘ └──────┬─────────┘                      │
            │        │        │       │  │           │                                │
            ▼        │        ▼       │  ▼           ▼                                │
     ┌───────────┐   │  ┌──────────┐  │ ┌──────────────┐                              │
     │ Postgres  │   │  │ Postgres │  │ │  Postgres    │   (RDS，database-per-service) │
     │ _project  │   │  │ _scan    │  │ │  _alert      │                              │
     └───────────┘   │  └──────────┘  │ └──────────────┘                              │
                     │                │                                               │
                     ▼                ▼                                               │
              ┌─────────────┐   ┌──────────┐    ┌──────────────────┐                  │
              │ S3 (manifest│   │  Redis   │    │  Kafka (MSK)     │                  │
              │  文件存储)   │   │ 缓存/限流 │    │  4 个 topic      │                  │
              └─────────────┘   └──────────┘    └──────────────────┘                  │
                                                        ▲                             │
                                              ┌─────────┴─────────┐                   │
                                              │  api.osv.dev      │  (外部)           │
                                              └───────────────────┘                   │
                        └─────────────────────────────────────────────────────────────┘
```

本地开发用 Docker Compose 跑同一套拓扑：Postgres / Redis / Kafka(KRaft) / 4 个 Java 服务 / Nginx 前端。

---

## 2. 核心流程

### 2.1 上传 → 扫描 → 推送（主链路）

```
 1. POST /api/projects/{id}/manifests  (multipart)
      project-service
        ├─ 存文件到 ManifestStorage (本地磁盘 / S3)
        ├─ 在同一个 DB 事务里：insert manifests + insert outbox_events(ScanRequested)
        └─ 立即返回 scanId  ← 前端马上订阅 /topic/scans/{scanId}

 2. OutboxPublisher (每秒轮询, SELECT ... FOR UPDATE SKIP LOCKED)
        └─ 发送 Kafka topic: scan.requested   key = projectId

 3. scan-service 消费 scan.requested
        ├─ 幂等检查：scans 表以 scanId 为主键，已存在且非 FAILED → 直接 ack 跳过
        ├─ 阶段 PARSING   → ManifestParser 解析成 DependencyGraph（节点 + 有向边）
        ├─ 阶段 QUERYING  → 按 1000 个一批调用 OSV /v1/querybatch
        │                   每批完成后发 scan.progress 事件
        ├─ 阶段 ENRICHING → 对每个唯一漏洞 ID 取详情 /v1/vulns/{id}
        │                   Redis 缓存 24h → 未命中再请求 → 落库 vulnerabilities
        ├─ 阶段 DIFFING   → 与该项目上一次扫描比对，标记 new / resolved
        ├─ 落库 scans / scan_dependencies / scan_edges / scan_findings
        └─ 发 scan.completed + N 条 vuln.detected（仅新增的）

 4. alert-service 消费三类事件
        ├─ processed_events 表按 eventId 去重（at-least-once → 效果 exactly-once）
        ├─ scan.progress  → WebSocket  /topic/scans/{scanId}
        ├─ vuln.detected  → 匹配用户 alert_rules(最低严重度) → 落库 alerts
        │                   → WebSocket /topic/users/{userId}/alerts
        │                   → 可选 Webhook(Slack 格式) / Email(SES)
        └─ scan.completed → 汇总告警
```

### 2.2 定时重扫（持续监控）

每天 03:00，scan-service 的 `RescanScheduler`（ShedLock 保证多实例只跑一个）：
对每个项目的最新成功扫描，**不重新解析 manifest**，直接用已存的依赖列表重新查 OSV。
OSV 每天都会新增漏洞，昨天干净的包今天可能就有 CVE——这就是"持续追踪"的价值。
只对**新增**的漏洞发 `vuln.detected`。

---

## 3. 关键技术决策（面试重点）

| 决策 | 选择 | 为什么 / 权衡 |
|---|---|---|
| 服务间认证 | 网关校验 JWT，下游信任 `X-User-Id` 头 | 下游服务在私有子网，只有网关能访问；避免每个服务重复解 JWT。代价是必须保证下游不对公网暴露（安全组 / K8s NetworkPolicy），网关要**剥离**客户端伪造的 X-User-* 头 |
| 服务发现 | 不用 Eureka，用 DNS | Docker Compose / ECS Service Connect / K8s Service 都自带 DNS 发现。Eureka 是在没有编排平台的年代的方案，加它只会多一个单点。面试时能解释清楚这点比"用了 Eureka"更加分 |
| 事件可靠投递 | Transactional Outbox | 直接在业务事务里 `kafkaTemplate.send()` 会出现"DB 提交了但 Kafka 没发出去"或反之。Outbox 把事件当业务数据一起提交，再异步搬运，保证 at-least-once |
| 消费幂等 | 业务主键 + processed_events 表 | Kafka 是 at-least-once，重平衡时会重复投递。scan-service 用 scanId 主键天然幂等；alert-service 用 eventId 去重表 |
| 大文件传递 | 事件只带 storage key，不带内容 | package-lock.json 轻松几 MB，Kafka 默认消息上限 1 MB。用 S3 / 共享卷做"Claim Check"模式 |
| 第三方 API 保护 | Resilience4j RateLimiter + Retry + CircuitBreaker | OSV 是公共免费服务，要礼貌调用；网络抖动要重试但要防止重试风暴（指数退避）；OSV 挂了要快速失败而不是拖垮线程池 |
| 漏洞详情缓存 | Redis (24h TTL) + Postgres 持久化 | 同一个 lodash 漏洞会被几百个项目命中，Redis 挡掉重复请求；Postgres 让 UI 查历史不依赖 OSV 在线 |
| 严重度计算 | 优先 GHSA `database_specific.severity`，否则自己算 CVSS 3.x 基础分 | OSV 返回的 `severity` 字段只有 CVSS 向量字符串，没有分数，必须自己实现 CVSS 3.1 公式 |
| 依赖图存储 | 节点表 + 边表，路径按需 BFS | 一个 React 项目 1500 个节点 / 3000 条边很正常。存邻接表，"为什么我的项目里有这个包"按需计算最短路径，不预存所有路径 |
| 定时任务 | ShedLock (JDBC) | 服务多实例部署时 `@Scheduled` 会每个实例都跑；ShedLock 用 DB 行锁做分布式锁，比引入 Quartz 集群轻得多 |
| 实时推送 | STOMP over WebSocket，网关代理 | 浏览器 WebSocket 不能设自定义 header，JWT 通过 `?token=` 查询参数传，网关在握手阶段校验 |
| 数据库 | database-per-service（同一 RDS 实例上 3 个库） | 保持服务边界清晰、各自迁移脚本独立；本地和低预算部署共享一个实例省钱 |

---

## 4. Kafka Topics 与事件

| Topic | 生产者 | 消费者 | Key | 说明 |
|---|---|---|---|---|
| `scan.requested` | project-service (outbox) | scan-service | projectId | 同一项目的扫描按序处理 |
| `scan.progress` | scan-service | alert-service | scanId | 高频、允许丢（UI 进度条） |
| `scan.completed` | scan-service | alert-service | projectId | 扫描汇总 |
| `vuln.detected` | scan-service | alert-service | userId | 每条新发现一个事件 |

死信：每个消费者配置 `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`，重试 3 次后进 `<topic>.DLT`。

事件类定义在 `backend/common/.../events/`，所有事件都带 `eventId`（UUID）和 `occurredAt`。

---

## 5. 数据模型

### project-service（`depwatch_project`）
- `users`：id, email(unique), password_hash(BCrypt), display_name
- `projects`：id, owner_id, name, description — unique(owner_id, name)
- `manifests`：id, project_id, file_name, storage_key, manifest_type, size_bytes, scan_id
- `outbox_events`：id, aggregate_type, aggregate_id, event_type, topic, partition_key, payload(jsonb), published_at, attempts

### scan-service（`depwatch_scan`）
- `scans`：id(=来自事件的 scanId), project_id, project_name, user_id, trigger(MANUAL/SCHEDULED), status, 各严重度计数, started_at, completed_at, error
- `scan_dependencies`：scan_id, node_id("npm:lodash@4.17.15"), ecosystem, name, version, direct, depth
- `scan_edges`：scan_id, parent_node_id, child_node_id
- `vulnerabilities`：id(OSV id), summary, details, severity, cvss_score, cvss_vector, aliases(jsonb), references(jsonb), published, modified, raw(jsonb)
- `scan_findings`：scan_id, node_id, vuln_id, fixed_version, is_new
- `shedlock`

### alert-service（`depwatch_alert`）
- `alerts`：id, user_id, project_id, scan_id, vuln_id, severity, title, message, read_at
- `alert_rules`：user_id(pk), min_severity, in_app, webhook_url, email
- `processed_events`：event_id(pk), processed_at

---

## 6. Manifest 解析器

`ManifestParser` 接口 + 三个实现，按文件名自动选择，可随时加 `requirements.txt` / `go.sum`：

| 解析器 | 输入 | 传递依赖 | 实现要点 |
|---|---|---|---|
| `NpmLockfileParser` | `package-lock.json` v2/v3 | ✅ 完整 | `packages` 是扁平 map，key 是 `node_modules/a/node_modules/b` 这种路径。边的解析要模拟 Node 的模块解析算法：从当前路径逐级向上找 `node_modules/<name>` |
| `MavenTreeParser` | `mvn dependency:tree` 文本 | ✅ 完整 | 用缩进（每层 3 字符 `+- ` / `\- ` / `\|  `）+ 栈重建树 |
| `MavenPomParser` | `pom.xml` | ❌ 仅直接依赖 | 要解析 `${property}` 占位符和 `<dependencyManagement>` 里的版本。真正的传递依赖要下载所有父 POM，所以推荐用户上传 dependency:tree |

输出统一为 `DependencyGraph { root, nodes: Map<nodeId, Node>, edges: Map<parent, Set<child>> }`。

---

## 7. 模块结构

```
depwatch/
├── backend/
│   ├── pom.xml                 父 POM：统一版本、依赖管理
│   ├── common/                 事件、Severity、UserContext 过滤器、ManifestStorage(Local/S3)、异常处理
│   ├── gateway/                Spring Cloud Gateway (WebFlux)
│   ├── project-service/
│   ├── scan-service/
│   ├── alert-service/
│   └── Dockerfile              多阶段构建，ARG MODULE 复用
├── frontend/                   Vite + React + TypeScript
├── infra/
│   ├── docker/                 Postgres 初始化脚本、Nginx 配置
│   └── aws/                    Terraform（低预算 EC2 方案）+ ECS 任务定义模板
├── scripts/                    check-env.sh / dev.sh / sample manifests
├── docker-compose.yml
├── .github/workflows/ci.yml
└── docs/
```

---

## 8. 可以继续加深的方向（简历加分项）

1. **SBOM 支持**：解析 CycloneDX / SPDX 格式（行业标准）。
2. **许可证合规**：从 npm registry / Maven Central 拉 license，标记 GPL 传染风险。
3. **GitHub App 集成**：安装到仓库后自动拉 lockfile，PR 上评论新引入的漏洞。
4. **OpenTelemetry 全链路追踪**：一次上传跨 3 个服务 + Kafka，trace 串起来非常直观。
5. **Kubernetes 部署**：Helm chart + HPA，scan-service 按 Kafka lag 自动扩容（KEDA）。
