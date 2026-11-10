#!/usr/bin/env bash
# Local development helper
#   scripts/dev.sh infra     start only Postgres / Redis / Kafka / Kafka UI (run the Java services from your IDE)
#   scripts/dev.sh all       start everything in containers
#   scripts/dev.sh demo      register a user, create projects, upload the sample lockfiles, print scanIds
#   scripts/dev.sh down      stop and remove all containers and volumes
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

case "${1:-}" in
  infra)
    docker compose up -d postgres redis kafka kafka-ui
    echo "Postgres localhost:5432 (depwatch/depwatch) · Redis :6379 · Kafka localhost:9094 · Kafka UI http://localhost:8090"
    echo "Now run each service from IDE, or: (cd backend && mvn -pl scan-service spring-boot:run)"
    ;;
  all)
    docker compose up -d --build
    echo "Frontend http://localhost:3000 · Gateway http://localhost:8080 · Kafka UI http://localhost:8090"
    ;;
  demo)
    GW=${GW:-http://localhost:8080}
    EMAIL="demo+$(date +%s)@depwatch.dev"
    echo "▶ register $EMAIL"
    TOKEN=$(curl -sf -X POST "$GW/api/auth/register" -H 'Content-Type: application/json' \
      -d "{\"email\":\"$EMAIL\",\"password\":\"password123\",\"displayName\":\"Demo\"}" | jq -r .accessToken)
    echo "▶ create project"
    PID=$(curl -sf -X POST "$GW/api/projects" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
      -d '{"name":"sample-vulnerable-app","description":"demo"}' | jq -r .id)
    echo "▶ upload package-lock.json"
    curl -sf -X POST "$GW/api/projects/$PID/manifests" -H "Authorization: Bearer $TOKEN" \
      -F "file=@scripts/samples/package-lock.json" | jq .
    echo "▶ upload dependency-tree.txt into a second project"
    PID2=$(curl -sf -X POST "$GW/api/projects" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
      -d '{"name":"legacy-java-service","description":"demo"}' | jq -r .id)
    curl -sf -X POST "$GW/api/projects/$PID2/manifests" -H "Authorization: Bearer $TOKEN" \
      -F "file=@scripts/samples/dependency-tree.txt" | jq .
    echo
    echo "Login at http://localhost:3000 with $EMAIL / password123 and watch the scans run."
    ;;
  down)
    docker compose down -v
    ;;
  *)
    sed -n '2,7p' "$0"
    ;;
esac
