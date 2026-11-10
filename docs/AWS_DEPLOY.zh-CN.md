# AWS 部署指南

两条路线，按预算和简历目标选：

| | 方案 A：单台 EC2 + Docker Compose | 方案 B：ECS Fargate 全托管 |
|---|---|---|
| 月成本 | **≈ $12–25**（t4g.medium 按需 + S3 + 少量流量；用 Spot 更便宜） | ≈ $120–200（Fargate × 4 + RDS + ElastiCache + MSK Serverless + ALB） |
| 简历关键词 | EC2, S3, CloudFront, ECR, IAM, Security Group, GitHub Actions | 以上 + ECS/Fargate, ALB, RDS, ElastiCache, MSK, Terraform, Secrets Manager |
| 适合 | 先跑起来、面试演示、长期挂着 | 想把"生产级云架构"讲透；可以部署完截图/录屏后销毁 |
| 本仓库提供 | `infra/aws/terraform/`（完整） + `scripts/deploy-ec2.sh` | `infra/aws/ecs/`（任务定义模板 + 架构说明） |

> **先做 A，再按需做 B。** 两者前端部分（S3 + CloudFront）完全一样。
> 面试时你可以说："开发/演示环境跑在一台 EC2 上用 Compose 编排，生产形态我设计成 ECS Fargate，任务定义在仓库里。"

---

## 0. 前置准备（两种方案都需要）

```bash
aws configure                                  # 用 IAM 用户的 key，不要用 root
aws sts get-caller-identity                    # 确认
export AWS_REGION=us-east-1
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
```

**务必先设预算告警**：Billing → Budgets → 创建 $20/月预算，80% 时邮件提醒。学生忘关资源被扣几百刀的事太多了。

---

## 方案 A：EC2 + Docker Compose + S3/CloudFront

### A.1 架构

```
Browser ─▶ CloudFront ─▶ S3 (前端静态文件)
   │
   └─/api,/ws─▶ EC2 公网 IP:8080 (gateway) ─▶ 同机 Compose 内的其他服务 + Postgres/Redis/Kafka
                     │
                     └─ manifest 文件 → S3 bucket (IAM Instance Profile，无需 AK/SK)
```

### A.2 用 Terraform 创建资源

```bash
cd infra/aws/terraform
cp terraform.tfvars.example terraform.tfvars    # 填 key pair 名、你的公网 IP
terraform init
terraform plan
terraform apply                                   # ≈ 3 分钟
```

创建了：
- 1 台 EC2（Amazon Linux 2023，arm64 t4g.medium，自动安装 Docker）
- 安全组：22 只允许你的 IP；8080 对 CloudFront/公网开放
- IAM Role + Instance Profile：允许读写 manifest bucket、拉 ECR 镜像
- 2 个 S3 bucket：前端静态站点、manifest 存储（私有）
- CloudFront 分发：`/` → S3，`/api/*` 和 `/ws*` → EC2
- 4 个 ECR 仓库

输出里有 `ec2_public_ip`、`cloudfront_domain`、`ecr_registry`。

### A.3 构建并推送镜像

```bash
aws ecr get-login-password | docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

# Apple Silicon 上构建 arm64 镜像给 t4g 用（如果用 t3 x86 实例，改成 --platform linux/amd64）
for m in gateway project-service scan-service alert-service; do
  docker build --platform linux/arm64 --build-arg MODULE=$m -t $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/depwatch/$m:latest backend/
  docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/depwatch/$m:latest
done
```

或直接用 `scripts/deploy-ec2.sh`，它会做完上面所有事 + 部署。

### A.4 在 EC2 上启动

```bash
ssh -i ~/.ssh/your-key.pem ec2-user@$(terraform output -raw ec2_public_ip)

# 拉取生产 compose 文件（deploy 脚本会 scp 上去），然后：
export DEPWATCH_JWT_SECRET=$(openssl rand -base64 48)
export DEPWATCH_S3_BUCKET=<terraform output manifest_bucket>
export ECR=<terraform output ecr_registry>
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

`docker-compose.prod.yml` 和开发版的区别：用 ECR 镜像而非本地 build、storage type=s3、不暴露 DB/Kafka 端口、加 `restart: always`。

### A.5 部署前端

```bash
cd frontend
npm ci && npm run build
aws s3 sync dist/ s3://$(terraform -chdir=../infra/aws/terraform output -raw frontend_bucket) --delete
aws cloudfront create-invalidation --distribution-id $(terraform -chdir=../infra/aws/terraform output -raw cloudfront_id) --paths "/*"
```

打开 `https://<cloudfront_domain>` 即可。

### A.6 HTTPS 和自定义域名（可选，$12/年）

Route 53 买个域名 → ACM 申请证书（us-east-1）→ CloudFront 绑定别名。Terraform 里有注释掉的配置块。

---

## 方案 B：ECS Fargate（生产形态）

### B.1 架构

```
Internet ─▶ CloudFront ─▶ S3 (前端)
                │
                └─/api─▶ ALB (HTTPS) ─▶ ECS Service: gateway (2 tasks)
                                            │ Service Connect (DNS: project-service.depwatch.local ...)
                              ┌─────────────┼─────────────┐
                        project-service  scan-service  alert-service      ← 私有子网，无公网 IP
                              │              │              │
                        RDS Postgres (Multi-AZ 可选)   ElastiCache Redis   MSK Serverless
                              └── manifests → S3 ──┘
                        Secrets Manager: JWT secret, DB password → 注入为环境变量
                        CloudWatch Logs: 每个服务一个 log group
```

### B.2 关键决策

| 问题 | 选择 | 理由 |
|---|---|---|
| 服务发现 | ECS Service Connect | 内置 DNS + Envoy sidecar，不需要 Eureka；比 Cloud Map 多了重试和指标 |
| Kafka | MSK Serverless | 按量付费，不用的时候几乎 $0；传统 MSK 最少 2 broker ≈ $150/月 |
| 密钥 | Secrets Manager → 任务定义 `secrets` | 密钥不进镜像、不进环境变量明文、不进 Git |
| 扩缩容 | scan-service 按 Kafka consumer lag 的 CloudWatch 指标 | 上传高峰自动加 task，闲时缩到 1 |
| 数据库 | 一个 RDS 实例三个 database | 省钱；真到了要独立扩容再拆实例 |
| 健康检查 | ALB → `/actuator/health/readiness` | Boot 的 probes.enabled=true 区分 liveness / readiness |

### B.3 任务定义模板

见 `infra/aws/ecs/task-definitions/*.json`。替换 `<ACCOUNT_ID>`、`<REGION>`、secret ARN 后：

```bash
aws ecs register-task-definition --cli-input-json file://infra/aws/ecs/task-definitions/scan-service.json
```

### B.4 用 Copilot 省事（推荐给个人项目）

AWS Copilot CLI 能用几条命令生成上面整套（VPC、ALB、ECS、Service Connect、日志）：

```bash
brew install aws/tap/copilot-cli
copilot app init depwatch
copilot env init --name prod
copilot svc init --name gateway --svc-type "Load Balanced Web Service" --dockerfile backend/Dockerfile
copilot svc init --name scan-service --svc-type "Backend Service" --dockerfile backend/Dockerfile
# ... 其余服务同理，然后
copilot svc deploy --name gateway --env prod
```

RDS / ElastiCache / MSK 用 `copilot storage init` 或 Terraform 另建，连接串通过 `copilot secret init` 注入。

---

## CI/CD（GitHub Actions）

`.github/workflows/ci.yml`：
1. PR → 跑 `mvn verify`（含单元测试）和 `npm run build`
2. push 到 `main` → 构建 4 个镜像推 ECR（用 OIDC 联邦身份，**不在 GitHub 存 AWS key**）→ SSH 到 EC2 执行 `docker compose pull && up -d` → 前端同步到 S3 + CloudFront 失效

OIDC 配置：在 IAM 里创建 Identity Provider `token.actions.githubusercontent.com`，再建一个 Role 信任你的仓库。Terraform 里有这个 Role（`github_actions_role`），输出 ARN 后填到 GitHub repo 的 Secrets `AWS_ROLE_ARN`。

---

## 成本控制清单

- [ ] Budget 告警已设
- [ ] 演示完不用时：`terraform destroy`（方案 A 全部资源 1 分钟销毁，数据丢失；要保留数据先 `aws s3 sync` 备份）
- [ ] 或者只停 EC2：`aws ec2 stop-instances --instance-ids ...`（停机不收计算费，只收 EBS ≈ $2/月）
- [ ] MSK Serverless / RDS 如果用了方案 B，**一定**记得删，这两个是大头
- [ ] CloudFront 和 S3 几乎免费（免费额度内）

---

## 常见问题

**EC2 上 Kafka 起不来 / OOM**：t4g.small（2 GB）不够，至少 t4g.medium（4 GB）。或者把 Kafka 的 `KAFKA_HEAP_OPTS=-Xmx512m` 加到 compose。

**CloudFront 返回 502 for /api**：源站协议要选 HTTP only（EC2 上没证书），端口 8080；检查安全组 8080 入站。

**WebSocket 连不上**：CloudFront 需要把 `/ws*` 行为的缓存策略设为 CachingDisabled，源请求策略 AllViewer；Terraform 已配置。

**上传 lockfile 报 413**：CloudFront 默认请求体上限 20 MB 够用；Nginx/网关的限制在配置里已调到 25 MB。
