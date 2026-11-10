#!/usr/bin/env bash
# Checks that the local dev environment is complete (see docs/SETUP_MAC.md)
set -u
ok=0; bad=0

check() {   # check <name> <command> <min-version-hint>
  local name="$1" cmd="$2" hint="$3"
  if command -v "${cmd%% *}" >/dev/null 2>&1; then
    local v; v=$($cmd 2>&1 | head -n1)
    printf "  ✅ %-16s %s\n" "$name" "$v"; ok=$((ok+1))
  else
    printf "  ❌ %-16s not found  (%s)\n" "$name" "$hint"; bad=$((bad+1))
  fi
}

echo "DepWatch dev environment check"
echo "──────────────────────────────"
check "Java 21"        "java -version"            "sdk install java 21.0.5-tem"
check "Maven 3.9"      "mvn -version"             "sdk install maven"
check "Node 20+"       "node -v"                  "nvm install --lts"
check "npm"            "npm -v"                   "comes with node"
check "Docker"         "docker --version"         "brew install --cask docker"
check "Docker Compose" "docker compose version"   "part of Docker Desktop"
check "Git"            "git --version"            "xcode-select --install"
check "AWS CLI"        "aws --version"            "brew install awscli  (only for deploy)"
check "Terraform"      "terraform -version"       "brew install terraform  (only for deploy)"
check "psql"           "psql --version"           "brew install postgresql@16  (optional)"
check "jq"             "jq --version"             "brew install jq  (optional)"

echo
if command -v docker >/dev/null 2>&1; then
  if docker info >/dev/null 2>&1; then
    echo "  ✅ Docker daemon is running"
    mem=$(docker info --format '{{.MemTotal}}' 2>/dev/null || echo 0)
    gb=$(( mem / 1024 / 1024 / 1024 ))
    if [ "$gb" -lt 6 ]; then
      echo "  ⚠️  Docker has only ${gb} GB RAM — raise to ≥ 6 GB in Docker Desktop → Settings → Resources"
    else
      echo "  ✅ Docker memory: ${gb} GB"
    fi
  else
    echo "  ❌ Docker daemon not running — open Docker Desktop"
    bad=$((bad+1))
  fi
fi

if command -v java >/dev/null 2>&1; then
  jv=$(java -version 2>&1 | awk -F'"' '/version/ {print $2}' | cut -d. -f1)
  [ "${jv:-0}" -ge 21 ] || { echo "  ⚠️  Java $jv detected; this project needs 21"; }
fi

echo
echo "$ok OK, $bad missing."
[ "$bad" -eq 0 ] && echo "You're ready: docker compose up -d --build" || exit 1
