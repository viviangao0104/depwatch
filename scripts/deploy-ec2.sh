#!/usr/bin/env bash
# 一键部署到方案 A 的 EC2：构建镜像 → 推 ECR → 同步 compose 文件 → 远程 pull & up → 发布前端
# 用法：scripts/deploy-ec2.sh ~/.ssh/my-key.pem
set -euo pipefail

KEY="${1:?usage: deploy-ec2.sh <path-to-ssh-key.pem>}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TF="$ROOT/infra/aws/terraform"

REGION=$(terraform -chdir="$TF" output -raw region 2>/dev/null || echo "${AWS_REGION:-us-east-1}")
ECR=$(terraform -chdir="$TF" output -raw ecr_registry)
IP=$(terraform -chdir="$TF" output -raw ec2_public_ip)
BUCKET_FE=$(terraform -chdir="$TF" output -raw frontend_bucket)
BUCKET_MF=$(terraform -chdir="$TF" output -raw manifest_bucket)
CF_ID=$(terraform -chdir="$TF" output -raw cloudfront_id)
CF_DOMAIN=$(terraform -chdir="$TF" output -raw cloudfront_domain)
TAG=$(git -C "$ROOT" rev-parse --short HEAD 2>/dev/null || date +%s)

echo "▶ Logging in to ECR $ECR"
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$ECR"

echo "▶ Building & pushing images (tag $TAG)"
for m in gateway project-service scan-service alert-service; do
  docker build --platform linux/arm64 --build-arg MODULE="$m" -t "$ECR/depwatch/$m:$TAG" -t "$ECR/depwatch/$m:latest" "$ROOT/backend"
  docker push "$ECR/depwatch/$m:$TAG"
  docker push "$ECR/depwatch/$m:latest"
done

echo "▶ Syncing compose files to EC2"
SSH="ssh -o StrictHostKeyChecking=no -i $KEY ec2-user@$IP"
scp -o StrictHostKeyChecking=no -i "$KEY" "$ROOT/docker-compose.prod.yml" ec2-user@"$IP":/opt/depwatch/
$SSH "mkdir -p /opt/depwatch/infra/docker"
scp -o StrictHostKeyChecking=no -i "$KEY" "$ROOT/infra/docker/postgres-init.sql" ec2-user@"$IP":/opt/depwatch/infra/docker/

echo "▶ Deploying on EC2"
$SSH bash -s <<EOF
  set -e
  cd /opt/depwatch
  if [ ! -f .env ]; then
    echo "DEPWATCH_JWT_SECRET=\$(openssl rand -base64 48)" > .env
    echo "POSTGRES_PASSWORD=\$(openssl rand -hex 16)" >> .env
  fi
  grep -q '^ECR=' .env && sed -i 's#^ECR=.*#ECR=$ECR#' .env || echo "ECR=$ECR" >> .env
  grep -q '^TAG=' .env && sed -i 's#^TAG=.*#TAG=$TAG#' .env || echo "TAG=$TAG" >> .env
  grep -q '^DEPWATCH_S3_BUCKET=' .env || echo "DEPWATCH_S3_BUCKET=$BUCKET_MF" >> .env
  grep -q '^AWS_REGION=' .env || echo "AWS_REGION=$REGION" >> .env
  grep -q '^DEPWATCH_CORS_ORIGINS=' .env || echo "DEPWATCH_CORS_ORIGINS=https://$CF_DOMAIN" >> .env
  aws ecr get-login-password --region $REGION | docker login --username AWS --password-stdin $ECR
  docker compose -f docker-compose.prod.yml pull
  docker compose -f docker-compose.prod.yml up -d --remove-orphans
  docker image prune -f
EOF

echo "▶ Building & publishing frontend"
(cd "$ROOT/frontend" && npm ci && npm run build)
aws s3 sync "$ROOT/frontend/dist/" "s3://$BUCKET_FE" --delete
aws cloudfront create-invalidation --distribution-id "$CF_ID" --paths "/*" >/dev/null

echo "✅ Deployed: https://$CF_DOMAIN"
