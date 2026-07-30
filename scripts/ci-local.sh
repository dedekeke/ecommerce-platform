#!/usr/bin/env bash
# Local CI replacement — run the same checks GitHub Actions would.
# Usage:
#   scripts/ci-local.sh                 # everything: backend + frontend + infra lint
#   scripts/ci-local.sh --backend       # backend only
#   scripts/ci-local.sh --frontend      # frontend only
#   scripts/ci-local.sh --infra         # k8s + helm lint only
#   scripts/ci-local.sh --quick         # skip slow steps (integration tests, build)
#   scripts/ci-local.sh --help

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# ---- Args ----
DO_BACKEND=true
DO_FRONTEND=true
DO_INFRA=true
QUICK=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --backend)  DO_FRONTEND=false; DO_INFRA=false ;;
    --frontend) DO_BACKEND=false;  DO_INFRA=false ;;
    --infra)    DO_BACKEND=false;  DO_FRONTEND=false ;;
    --quick)    QUICK=true ;;
    --help|-h)
      sed -n '2,11p' "$0"; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; exit 2 ;;
  esac
  shift
done

# ---- Helpers ----
RED=$'\e[31m'; GREEN=$'\e[32m'; YELLOW=$'\e[33m'; BLUE=$'\e[34m'; RESET=$'\e[0m'
START_TS=$(date +%s)
declare -a FAILED=()

step() {
  local name=$1; shift
  echo
  echo "${BLUE}━━━ $name ━━━${RESET}"
  if "$@"; then
    echo "${GREEN}✓ $name${RESET}"
  else
    echo "${RED}✗ $name${RESET}"
    FAILED+=("$name")
  fi
}

# ---- Backend ----
backend_unit_tests() {
  if $QUICK; then
    mvn -B -ntp -DskipITs -pl '!integration-tests' test
  else
    mvn -B -ntp test
  fi
}
backend_build() { mvn -B -ntp -DskipTests clean install; }

# ---- Frontend ----
REACT_APPS=(shell-app product-catalog-mfe cart-mfe checkout-mfe)
ANGULAR_APPS=(user-dashboard-mfe admin-dashboard-mfe)

react_app() {
  local app=$1
  pushd "frontend/$app" >/dev/null
  if [[ ! -d node_modules ]]; then npm ci || npm install; fi
  npm audit --audit-level=critical
  npm run lint --if-present
  npm test --if-present -- --run
  popd >/dev/null
}

angular_app() {
  local app=$1
  pushd "frontend/$app" >/dev/null
  if [[ ! -d node_modules ]]; then npm ci || npm install; fi
  npm audit --audit-level=critical
  npm run lint --if-present
  if $QUICK; then
    npx ng test --watch=false --browsers=ChromeHeadless --no-progress --code-coverage=false
  else
    npx ng test --watch=false --browsers=ChromeHeadless --no-progress --code-coverage
  fi
  popd >/dev/null
}

frontend_all() {
  for app in "${REACT_APPS[@]}";    do step "Frontend: $app"    react_app    "$app"; done
  for app in "${ANGULAR_APPS[@]}";  do step "Frontend: $app"    angular_app  "$app"; done
}

# ---- Infra lint ----
kustomize_lint() {
  command -v kubectl >/dev/null || { echo "${YELLOW}skip — kubectl not installed${RESET}"; return 0; }
  kubectl kustomize k8s/base       >/dev/null
  kubectl kustomize k8s/overlays/staging    >/dev/null
  kubectl kustomize k8s/overlays/production >/dev/null
}
helm_lint() {
  command -v helm >/dev/null || { echo "${YELLOW}skip — helm not installed${RESET}"; return 0; }
  helm lint ./helm/ecommerce
}

# ---- Run ----
echo "${BLUE}Local CI — backend=$DO_BACKEND frontend=$DO_FRONTEND infra=$DO_INFRA quick=$QUICK${RESET}"

# Config guards — fast, always run (match quality.yml guard jobs).
step "Prod log-level guard" bash scripts/check-prod-log-levels.sh
step "Dockerfile reactor-pom guard" bash scripts/check-dockerfile-reactor-poms.sh

if $DO_BACKEND;  then
  step "Backend build (skip tests)" backend_build
  step "Backend unit tests"          backend_unit_tests
fi

if $DO_FRONTEND; then
  frontend_all
fi

if $DO_INFRA; then
  step "Kustomize render"  kustomize_lint
  step "Helm lint"         helm_lint
fi

# ---- Summary ----
ELAPSED=$(( $(date +%s) - START_TS ))
echo
echo "${BLUE}━━━ Summary (${ELAPSED}s) ━━━${RESET}"
if [[ ${#FAILED[@]} -eq 0 ]]; then
  echo "${GREEN}✓ all green${RESET}"
  exit 0
else
  echo "${RED}✗ ${#FAILED[@]} failed:${RESET}"
  for f in "${FAILED[@]}"; do echo "  - $f"; done
  exit 1
fi
