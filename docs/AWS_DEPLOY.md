# AWS Deployment Guide

Two routes — pick by budget and by what you want on your resume:

| | Option A: one EC2 + Docker Compose | Option B: ECS Fargate, fully managed |
|---|---|---|
| Monthly cost | **≈ $12–25** (t4g.medium on-demand + S3 + light traffic; cheaper with Spot) | ≈ $120–200 (4× Fargate + RDS + ElastiCache + MSK Serverless + ALB) |
| Resume keywords | EC2, S3, CloudFront, ECR, IAM, Security Groups, GitHub Actions | all of those + ECS/Fargate, ALB, RDS, ElastiCache, MSK, Terraform, Secrets Manager |
| Best for | getting it live, interview demos, leaving it running | explaining a production-grade cloud topology; deploy, screenshot/record, then tear down |
| In this repo | `infra/aws/terraform/` (complete) + `scripts/deploy-ec2.sh` | `infra/aws/ecs/` (task-definition templates + notes) |

> **Do A first, then B if you want.** The frontend part (S3 + CloudFront) is identical in both.
> In an interview: "The dev/demo environment runs on a single EC2 with Compose; the production shape I designed is ECS Fargate — the task definitions are in the repo."

[中文版](AWS_DEPLOY.zh-CN.md)

---

## 0. Prerequisites (both options)

```bash
aws configure                                  # use an IAM user's key, never root
aws sts get-caller-identity                    # verify
export AWS_REGION=us-east-1
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
```

**Set a budget alert first**: Billing → Budgets → $20/month, email at 80%. Students forgetting to tear down resources and getting a $300 bill is a cliché for a reason.

---

## Option A: EC2 + Docker Compose + S3/CloudFront

### A.1 Architecture

```
Browser ─▶ CloudFront ─▶ S3 (frontend static files)
   │
   └─/api,/ws─▶ EC2 public IP:8080 (gateway) ─▶ other services + Postgres/Redis/Kafka in Compose on the same host
                     │
                     └─ manifest files → S3 bucket (IAM Instance Profile — no access keys in code)
```

### A.2 Create resources with Terraform

```bash
cd infra/aws/terraform
cp terraform.tfvars.example terraform.tfvars    # fill in key pair name, your public IP
terraform init
terraform plan
terraform apply                                   # ≈ 3 minutes
```

This creates:
- 1 EC2 instance (Amazon Linux 2023, arm64 t4g.medium, Docker installed via user-data)
- Security group: port 22 only from your IP; 8080 open for CloudFront/public
- IAM role + instance profile: read/write the manifest bucket, pull from ECR
- 2 S3 buckets: frontend static site, private manifest storage
- CloudFront distribution: `/` → S3, `/api/*` and `/ws*` → EC2
- 4 ECR repositories

Outputs include `ec2_public_ip`, `cloudfront_domain`, `ecr_registry`.

### A.3 Build and push images

```bash
aws ecr get-login-password | docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

# Build arm64 images on Apple Silicon for t4g (use --platform linux/amd64 for a t3 x86 instance)
for m in gateway project-service scan-service alert-service; do
  docker build --platform linux/arm64 --build-arg MODULE=$m -t $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/depwatch/$m:latest backend/
  docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/depwatch/$m:latest
done
```

Or run `scripts/deploy-ec2.sh`, which does all of the above plus deployment.

### A.4 Start on EC2

```bash
ssh -i ~/.ssh/your-key.pem ec2-user@$(terraform output -raw ec2_public_ip)

# the deploy script scp's the production compose file; then:
export DEPWATCH_JWT_SECRET=$(openssl rand -base64 48)
export DEPWATCH_S3_BUCKET=<terraform output manifest_bucket>
export ECR=<terraform output ecr_registry>
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

`docker-compose.prod.yml` differs from the dev file: ECR images instead of local builds, storage type = s3, no exposed DB/Kafka ports, `restart: always`, logs shipped to CloudWatch.

### A.5 Publish the frontend

```bash
cd frontend
npm ci && npm run build
aws s3 sync dist/ s3://$(terraform -chdir=../infra/aws/terraform output -raw frontend_bucket) --delete
aws cloudfront create-invalidation --distribution-id $(terraform -chdir=../infra/aws/terraform output -raw cloudfront_id) --paths "/*"
```

Open `https://<cloudfront_domain>`.

### A.6 HTTPS and a custom domain (optional, ~$12/year)

Buy a domain in Route 53 → request an ACM certificate (us-east-1) → attach it as a CloudFront alias. The Terraform has the relevant block commented out.

---

## Option B: ECS Fargate (production shape)

### B.1 Architecture

```
Internet ─▶ CloudFront ─▶ S3 (frontend)
                │
                └─/api─▶ ALB (HTTPS) ─▶ ECS Service: gateway (2 tasks)
                                            │ Service Connect (DNS: project-service.depwatch.local …)
                              ┌─────────────┼─────────────┐
                        project-service  scan-service  alert-service      ← private subnets, no public IP
                              │              │              │
                        RDS Postgres (Multi-AZ optional)  ElastiCache Redis   MSK Serverless
                              └── manifests → S3 ──┘
                        Secrets Manager: JWT secret, DB password → injected as env vars
                        CloudWatch Logs: one log group per service
```

### B.2 Key decisions

| Question | Choice | Why |
|---|---|---|
| Service discovery | ECS Service Connect | Built-in DNS + Envoy sidecar; no Eureka; adds retries and metrics over Cloud Map |
| Kafka | MSK Serverless | Pay per use, ~$0 when idle; classic MSK needs ≥ 2 brokers ≈ $150/month |
| Secrets | Secrets Manager → task-definition `secrets` | Secrets never in images, plain env vars, or Git |
| Autoscaling | scan-service on Kafka consumer lag (CloudWatch metric) | Scale out on upload bursts, back to 1 when idle |
| Database | one RDS instance, three databases | Saves money; split instances only when independent scaling is actually needed |
| Health checks | ALB → `/actuator/health/readiness` | Spring Boot's `probes.enabled=true` separates liveness from readiness |

### B.3 Task-definition templates

See `infra/aws/ecs/task-definitions/*.json`. Replace `<ACCOUNT_ID>`, `<REGION>` and the secret ARNs, then:

```bash
aws ecs register-task-definition --cli-input-json file://infra/aws/ecs/task-definitions/scan-service.json
```

### B.4 Shortcut: AWS Copilot (recommended for a personal project)

Copilot generates the whole stack (VPC, ALB, ECS, Service Connect, logging) from a few commands:

```bash
brew install aws/tap/copilot-cli
copilot app init depwatch
copilot env init --name prod
copilot svc init --name gateway --svc-type "Load Balanced Web Service" --dockerfile backend/Dockerfile
copilot svc init --name scan-service --svc-type "Backend Service" --dockerfile backend/Dockerfile
# … same for the other services, then
copilot svc deploy --name gateway --env prod
```

Create RDS / ElastiCache / MSK with `copilot storage init` or Terraform, and inject connection strings via `copilot secret init`.

---

## CI/CD (GitHub Actions)

`.github/workflows/ci.yml`:
1. On PR → `mvn verify` (with unit tests) and `npm run build`
2. On push to `main` with the repository variable `DEPLOY_ENABLED=true` → build 4 images and push to ECR (via OIDC federation — **no AWS keys stored in GitHub**) → roll out on EC2 through SSM `docker compose pull && up -d` → sync the frontend to S3 + invalidate CloudFront

OIDC setup: create the Identity Provider `token.actions.githubusercontent.com` in IAM and a role that trusts your repo. Terraform creates this role (`github_actions_role`); put its ARN in the repo secret `AWS_ROLE_ARN`, plus `EC2_INSTANCE_ID`, `FRONTEND_BUCKET`, `CLOUDFRONT_ID`.

---

## Cost-control checklist

- [ ] Budget alert set
- [ ] When not demoing: `terraform destroy` (Option A tears down in a minute; data is lost — `aws s3 sync` a backup first if you care)
- [ ] Or just stop the instance: `aws ec2 stop-instances --instance-ids …` (no compute charge while stopped; EBS ≈ $2/month)
- [ ] If you used Option B, **delete MSK Serverless and RDS** — they're the big-ticket items
- [ ] CloudFront and S3 are effectively free within the free tier

---

## FAQ

**Kafka won't start on EC2 / OOM**: t4g.small (2 GB) is too small; use at least t4g.medium (4 GB), or add `KAFKA_HEAP_OPTS=-Xmx512m` to compose.

**CloudFront returns 502 for /api**: the origin protocol must be HTTP only (no certificate on EC2), port 8080; check the security group allows 8080 inbound.

**WebSocket won't connect**: the `/ws*` behavior in CloudFront needs the CachingDisabled cache policy and the AllViewer origin-request policy — the Terraform already sets both.

**Upload returns 413**: CloudFront's default request-body limit is 20 MB, plenty; the Nginx/gateway limits are already raised to 25 MB.
