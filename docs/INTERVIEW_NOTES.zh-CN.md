# 面试讲解笔记

> 目的：让你能在 2 分钟内讲清楚项目，并且在面试官追问任何一个点时都有东西说。
> **先把代码读一遍再看这个**——面试官一眼能看出你是背稿子还是真做过。

---

## 一、2 分钟电梯版

> "我做了一个开源依赖漏洞追踪平台，类似 Dependabot / Snyk 的核心功能。
> 用户上传 package-lock.json 或 Maven 依赖树，系统解析出完整的依赖图——包括传递依赖——
> 批量比对 OSV 漏洞数据库，算出每个漏洞的严重度和修复版本，并且每天自动重新检查，
> 有新漏洞通过 WebSocket 和 Slack webhook 实时通知。
>
> 架构上是 4 个 Spring Boot 微服务：网关做 JWT 和限流，project-service 管用户和上传，
> scan-service 是核心的异步扫描流水线，alert-service 负责通知。服务间用 Kafka 解耦，
> 用了 Transactional Outbox 保证事件不丢，消费端做了幂等。部署在 AWS 上：
> 前端 S3 + CloudFront，后端容器化跑在 EC2 上，用 Terraform 管资源，GitHub Actions 做 CI/CD。"

然后停下来，让面试官选方向追问。

---

## 二、简历怎么写（3–4 条 bullet）

```
DepWatch — Open-Source Dependency Vulnerability Tracker        Java 21, Spring Boot 3, Kafka, Redis, PostgreSQL, React, AWS

• Designed and built a 4-service event-driven platform (Spring Cloud Gateway, Kafka, PostgreSQL) that parses
  npm/Maven dependency graphs, batch-queries the OSV vulnerability API, and continuously re-scans projects daily.
• Implemented the Transactional Outbox pattern and idempotent consumers to guarantee at-least-once event delivery
  with exactly-once effects across service boundaries; dead-letter topics for poison messages.
• Built a resilient third-party API client with Resilience4j (rate limiting, exponential-backoff retry, circuit
  breaker) and Redis caching, cutting OSV calls by ~90% on repeated packages; implemented CVSS 3.1 scoring.
• Deployed to AWS (EC2, S3, CloudFront, ECR, IAM) with Terraform and a GitHub Actions OIDC pipeline; designed the
  ECS Fargate production topology with Service Connect, RDS, ElastiCache and MSK Serverless.
```

> 注意最后那个 "~90%" 之类的数字要是你自己测出来的。跑一次 demo，看 Redis 命中日志，算一下，别编。

---

## 三、必问问题 & 答法

### Q1: 为什么要拆微服务？一个单体不行吗？

**诚实回答**：这个规模的项目单体完全够用，拆服务是为了练习和展示。但拆分边界不是随便切的：
- project-service 是**同步、面向用户的 CRUD**，负载模式是请求-响应；
- scan-service 是**异步、CPU 和 IO 密集的批处理**，一次扫描可能跑几分钟，需要独立扩缩容；
- alert-service 持有 **WebSocket 长连接**，扩缩容和部署节奏跟前两者完全不同。

三者的负载特征不同，这是拆分的真正理由。如果三者都是 CRUD，我就不拆。

### Q2: 讲讲 Transactional Outbox，为什么需要它？

**问题**：用户上传后要（a）写 DB（b）发 Kafka。两个操作不在一个事务里：
- 先发 Kafka 再写 DB：DB 失败 → scan-service 收到一个不存在的扫描请求；
- 先写 DB 再发 Kafka：Kafka 失败 → 用户看到上传成功但永远没结果。

**解法**：把事件当业务数据，和 manifests 表在**同一个本地事务**里写进 outbox_events 表。
后台 OutboxPublisher 每秒轮询未发布的行，发 Kafka，成功后标记。

**细节**（面试官会追）：
- 多实例轮询会不会重复发？`SELECT ... FOR UPDATE SKIP LOCKED`，每个实例锁不同的行。
- 发送成功但标记失败怎么办？会重发 → **at-least-once**，所以消费端必须幂等。
- 延迟？1 秒轮询对这个场景够了。要更低延迟用 Debezium 做 CDC 读 WAL，但多一个组件。
- 代码：`project-service/outbox/`

### Q3: 消费端怎么做幂等？

两种策略，分别用在两个服务里：
- **scan-service**：业务主键天然幂等。scanId 由 project-service 生成，是 scans 表主键。重复事件进来一查已存在就跳过。
- **alert-service**：事件没有天然业务键，用 **processed_events 表**记 eventId。关键是这个 INSERT 和告警写入在**同一个事务**里——要么都提交，要么都回滚。
- 代码：`ScanProcessor.process()` 开头、`IdempotencyGuard`

### Q4: 为什么事件里不直接带文件内容？

Kafka 默认消息上限 1 MB，一个中型 React 项目的 package-lock.json 轻松 2–5 MB。
用 **Claim Check 模式**：文件存 S3（本地是共享卷），事件只带 key。
顺便解耦了存储实现——`ManifestStorage` 接口两个实现，配置切换。

### Q5: 调 OSV 这种第三方 API 要注意什么？

三件事，对应 Resilience4j 三个注解（`OsvHttpClient`）：
1. **RateLimiter**：它是免费公共服务，我们每秒最多 20 次。拿不到令牌就等，不是失败。
2. **Retry**：网络抖动、5xx 重试，**指数退避**避免重试风暴；4xx 是我们的错，不重试。
3. **CircuitBreaker**：OSV 挂了，失败率超 50% 直接熔断 30 秒。否则每个请求都等 30 秒超时，线程池很快被占满，整个 scan-service 跟着挂——这叫级联故障。

另外 **Redis 缓存**漏洞详情 24 小时：lodash 的某个 CVE 会被几百个项目命中，没必要每次都问 OSV。
缓存层放在 RateLimiter 外面，命中时不消耗令牌——这是一个顺序问题，`OsvClient` 注释里有说。

### Q6: 依赖图是怎么解析的？传递依赖怎么拿到？

**npm**：package-lock.json v2/v3 的 `packages` 是扁平 map，key 是安装路径。难点是还原"谁依赖谁"：
a 依赖 b，但 b 可能在 `node_modules/b`（提升了）也可能在 `node_modules/a/node_modules/b`（版本冲突被嵌套）。
要**模拟 Node 的模块解析算法**：从 a 的路径开始逐级向上找 `node_modules/b`。这正是 npm hoisting 的逆过程。
- 代码：`NpmLockfileParser.resolve()`，有单元测试覆盖嵌套冲突场景。

**Maven**：pom.xml 只有直接依赖，传递依赖要下载所有父 POM 和 BOM 做版本仲裁——等于重写 Maven。
所以我选择让用户上传 `mvn dependency:tree` 的输出，用缩进 + 栈重建树。pom.xml 也支持但只做直接依赖，并明确提示用户。
**这是一个有意识的取舍**：80% 的价值，5% 的复杂度。

**存储**：节点表 + 边表（邻接表）。"这个有漏洞的包是谁引进来的"用 BFS 算最短路径，按需计算不预存。

### Q7: 严重度怎么算的？

OSV 返回的 `severity` 只有 CVSS 向量字符串（`CVSS:3.1/AV:N/AC:L/...`），**没有分数**。
我实现了 CVSS 3.1 基础分公式（`CvssV3Calculator`），用 NVD 官方计算器的值做了参数化单元测试。
优先级：GHSA 给的定性等级 > 自己算的 CVSS 分数映射 > UNKNOWN。

### Q8: 怎么保证"持续监控"？定时任务多实例怎么办？

每天凌晨 `RescanScheduler` 对每个项目最新一次扫描的依赖列表**重新查 OSV**（不重新解析文件），
和上次结果 diff，只对**新增**的漏洞发告警。昨天干净的包今天出 CVE 是常态，这就是产品的核心价值。

多实例：`@Scheduled` 每个实例都会触发。用 **ShedLock** 在 DB 里加分布式锁，`lockAtLeastFor` 防止时钟偏差导致重复执行。
为什么不用 Quartz 集群？重。为什么不用 Redis 锁？多一个故障点，而且 DB 本来就有。

### Q9: 网关做了什么？下游为什么信任 X-User-Id？

网关：JWT 校验、限流（Redis 令牌桶，按用户维度）、CORS、WebSocket 代理。
校验通过后把用户 ID 放 `X-User-Id` 头转发。下游服务直接读这个头，不再解 JWT。

**前提**：下游不能从公网访问（安全组 / 私有子网），**且网关必须剥离客户端自带的 X-User-* 头**——否则谁都能伪造身份。
代码里 `JwtAuthGlobalFilter` 第一件事就是 remove 这两个头。

**替代方案**：每个服务都校验 JWT（更安全但重复）、mTLS（更安全但运维复杂）。对这个规模，网关信任模型是合理的。

### Q10: WebSocket 怎么鉴权？多实例怎么办？

浏览器 WebSocket API 不能设 header，JWT 放 `?token=` 查询参数，网关在握手时校验。

多实例问题：alert-service 用的是内存 SimpleBroker，用户连在 A 实例上，事件被 B 实例消费，推不到。
解法：(1) 粘性会话 + 每个实例都消费 progress 事件（不同 consumer group）；(2) 换成外部 STOMP broker（RabbitMQ）。
**我目前是单实例，但知道这个问题和解法**——这句话很重要，说明你想过。

### Q11: 为什么没用 Eureka / Spring Cloud Config / Feign？

- **Eureka**：Docker Compose、ECS、K8s 都自带 DNS 服务发现。Eureka 是没有编排平台时代的方案。
- **Config Server**：环境变量 + Secrets Manager 够了，12-factor。
- **Feign**：服务间没有同步 HTTP 调用——全部走 Kafka 异步。这是刻意的：同步调用会让 scan-service 的慢拖累 project-service。

能说出"为什么不用"比"用了"更能体现判断力。

### Q12: 测试策略？

- 单元测试集中在**纯逻辑、高风险**的地方：三个解析器、CVSS 计算、版本比较。这些错了整个结果就错了。
- 集成测试（可以加）：Testcontainers 起 Postgres + Kafka，端到端跑一次上传 → 扫描 → 告警。
- 没有给 Controller 写测试，因为它们只是转发。

### Q13: 遇到的最难的 bug / 最大的坑？

准备 2 个你**真实**遇到的。可能的候选：
- npm 嵌套依赖解析搞错了，先按名字全局查找，发现 a 依赖的 b 版本不对；
- Spring Cache 和 Resilience4j 注解的执行顺序，缓存命中还在消耗限流令牌；
- Kafka 消费者 `max.poll.interval.ms` 默认 5 分钟，大项目扫描超时被踢出消费组，导致重复消费。

---

## 四、可以主动聊的扩展方向

- **SBOM**（CycloneDX/SPDX）支持——行业标准，美国政府采购要求
- **GitHub App**：自动拉仓库 lockfile，PR 上评论新引入的漏洞（Dependabot 的核心体验）
- **OpenTelemetry**：一次上传跨 3 个服务 + Kafka，分布式追踪能看到完整链路
- **KEDA** 按 Kafka lag 自动扩缩 scan-service
- **可达性分析**：漏洞函数在我的代码里有没有被调用？（Snyk 的高级功能，难）

---

## 五、Demo 演示脚本（3 分钟）

1. 打开前端，登录（提前注册好）
2. 展示两个项目的概览卡片，严重度条
3. 新建项目，拖入 `scripts/samples/package-lock.json`
4. **指着实时进度条讲**：Parsing → Querying（这里在调 OSV）→ Enriching → Diffing
5. 自动跳到结果页：讲 stats、点一个 CRITICAL finding 打开抽屉，讲 fixed version 和引入路径
6. 切到 Dependency graph tab，点一个 transitive 包的 "why?"
7. Alerts 页：刚才的告警已经在了（实时推送来的）；展示规则配置
8. 如果时间够：打开 Kafka UI 看 4 个 topic 的消息；打开 `/actuator/circuitbreakers`

演示前把 Docker 起好、注册好账号、确认 OSV 能访问。**录一个备用视频**以防网络问题。
