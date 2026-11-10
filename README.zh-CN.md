# DepWatch

**开源依赖漏洞追踪平台** — 上传 `package-lock.json` / `pom.xml` / `mvn dependency:tree` 输出，
解析完整依赖图，比对 [OSV](https://osv.dev) 漏洞数据库，每日自动重扫，新漏洞实时推送。

Java 21 · Spring Boot 3.3 · Spring Cloud Gateway · Kafka · PostgreSQL · Redis · React 18 · Docker · AWS (Terraform)

```
Browser ─▶ gateway (JWT, 限流, WS 代理)
             ├─▶ project-service  用户 / 项目 / manifest 上传 / Transactional Outbox
             ├─▶ scan-service     解析依赖图 → OSV 批量查询 → CVSS 评分 → 增量 diff → 定时重扫
             └─▶ alert-service    事件去重 → 告警规则 → WebSocket / Slack webhook
                        │ Kafka: scan.requested · scan.progress · scan.completed · vuln.detected
```

详细设计见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

---

## 快速开始

### 0. 环境

新 Mac 请先看 [docs/SETUP_MAC.md](docs/SETUP_MAC.md)，装完后：

```bash
scripts/check-env.sh
```

### 1. 一键启动（全部容器化）

```bash
docker compose up -d --build        # 第一次要下载依赖和构建镜像，约 5–8 分钟
```

| 地址 | 说明 |
|---|---|
| http://localhost:3000 | 前端 |
| http://localhost:8080 | 网关（API 入口） |
| http://localhost:8090 | Kafka UI，看 topic 和消息 |
| http://localhost:8081/swagger-ui.html | project-service API 文档（其他服务同理 8082 / 8083） |

### 2. 跑一个 demo

```bash
scripts/dev.sh demo
```

会注册一个用户、建两个项目、上传 `scripts/samples/` 里的两个样例 manifest（故意含有已知漏洞的旧版本）。
用脚本打印的账号登录前端，就能看到扫描实时进行。

### 3. 在 IDE 里开发

```bash
scripts/dev.sh infra                # 只起 Postgres / Redis / Kafka
cd backend && mvn -q install -DskipTests    # 先把 common 装进本地仓库
```

然后在 IntelliJ 里分别运行 4 个 `*Application` 主类（它们默认连 localhost 的基础设施）。
前端：

```bash
cd frontend && npm install && npm run dev   # http://localhost:5173，已配置代理到网关
```

### 4. 跑测试

```bash
cd backend && mvn test
```

单元测试覆盖三个 manifest 解析器、CVSS 3.1 计算器、版本比较器——这些是正确性的核心。

---

## 项目结构

```
backend/
  common/            事件定义、Severity、用户上下文过滤器、ManifestStorage(Local/S3)、统一异常
  gateway/           Spring Cloud Gateway：JwtAuthGlobalFilter、Redis 限流、路由
  project-service/   auth/  project/  outbox/
  scan-service/      parser/(npm·maven)  graph/  osv/(Resilience4j)  vuln/(CVSS)  scan/(流水线·调度)
  alert-service/     alert/  idempotency/  ws/(STOMP)  notify/(webhook)
frontend/            Vite + React + TS：登录、项目、实时进度、扫描结果、依赖路径、告警
infra/
  docker/            Postgres 多库初始化
  aws/terraform/     方案 A：EC2 + S3 + CloudFront + ECR + IAM + GitHub OIDC
  aws/ecs/           方案 B：Fargate 任务定义模板 + 说明
scripts/             check-env.sh · dev.sh · deploy-ec2.sh · samples/
docs/                ARCHITECTURE · SETUP_MAC · AWS_DEPLOY · INTERVIEW_NOTES
.github/workflows/   CI（测试+构建）/ CD（ECR → EC2 via SSM，S3 → CloudFront）
```

---

## 部署到 AWS

见 [docs/AWS_DEPLOY.md](docs/AWS_DEPLOY.md)。低预算方案（≈ $15/月）：

```bash
cd infra/aws/terraform && cp terraform.tfvars.example terraform.tfvars   # 填写
terraform init && terraform apply
cd ../../.. && scripts/deploy-ec2.sh ~/.ssh/your-key.pem
```

---

## 面试准备

[docs/INTERVIEW_NOTES.md](docs/INTERVIEW_NOTES.md)：2 分钟介绍、简历 bullet、13 个必问问题的答法、demo 脚本。

---

## 支持的 manifest

| 文件 | 传递依赖 | 怎么拿到 |
|---|---|---|
| `package-lock.json` (v1/v2/v3) | ✅ | 项目根目录自带 |
| `dependency-tree.txt` | ✅ | `mvn dependency:tree -DoutputFile=dependency-tree.txt` |
| `pom.xml` | 仅直接依赖 | 项目根目录自带 |

## License

MIT
