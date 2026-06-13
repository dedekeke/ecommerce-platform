#!/usr/bin/env bash
# Discover and run the test suite of every micro-frontend under frontend/*.
#
# Auto-detects the framework per package.json:
#   - React/Vite MFEs (vitest)  -> `npm test -- --run`
#   - Angular MFEs   (ng test)  -> `npx ng test --watch=false --browsers=ChromeHeadless --no-progress`
#
# Usage:
#   scripts/test-frontend-all.sh                # every MFE
#   scripts/test-frontend-all.sh --affected     # only MFEs with staged git changes (pre-commit)
#   scripts/test-frontend-all.sh --lint         # also run `npm run lint` where present
#   scripts/test-frontend-all.sh --help
#
# Exit codes: 0 all passed, 1 one or more failed, 2 setup error.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FRONTEND_DIR="$ROOT/frontend"

AFFECTED=false
WITH_LINT=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --affected) AFFECTED=true ;;
    --lint)     WITH_LINT=true ;;
    --help|-h)  sed -n '2,16p' "$0"; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; exit 2 ;;
  esac
  shift
done

command -v npm >/dev/null || { echo "[ERROR] npm not on PATH." >&2; exit 2; }
[[ -d "$FRONTEND_DIR" ]] || { echo "[ERROR] $FRONTEND_DIR not found." >&2; exit 2; }

BLUE=$'\e[34m'; GREEN=$'\e[32m'; RED=$'\e[31m'; YELLOW=$'\e[33m'; RESET=$'\e[0m'
declare -a FAILED=()
declare -a RAN=()

# An MFE is "affected" if it has staged changes under its directory.
is_affected() {
  local rel="frontend/$1"
  git -C "$ROOT" diff --cached --name-only 2>/dev/null | grep -q "^${rel}/"
}

has_script() { # has_script <dir> <script-name>
  node -e "process.exit(require('$1/package.json').scripts?.['$2'] ? 0 : 1)" 2>/dev/null
}
is_angular() { # is_angular <dir>
  node -e "const p=require('$1/package.json');process.exit((p.dependencies?.['@angular/core']||p.devDependencies?.['@angular/core'])?0:1)" 2>/dev/null
}

run_mfe() {
  local app="$1" dir="$FRONTEND_DIR/$1"
  echo
  echo "${BLUE}=== $app ===${RESET}"

  if [[ ! -d "$dir/node_modules" ]]; then
    echo "[$app] installing deps..."
    ( cd "$dir" && { npm ci || npm install; } )
  fi

  if $WITH_LINT && has_script "$dir" lint; then
    ( cd "$dir" && npm run lint )
  fi

  if is_angular "$dir"; then
    ( cd "$dir" && npx ng test --watch=false --browsers=ChromeHeadless --no-progress )
  else
    # vitest: `--run` makes it single-shot (no watch). `--` forwards the flag.
    ( cd "$dir" && npm test -- --run )
  fi
}

for dir in "$FRONTEND_DIR"/*/; do
  app="$(basename "$dir")"
  [[ -f "$dir/package.json" ]] || continue
  if $AFFECTED && ! is_affected "$app"; then
    continue
  fi
  RAN+=("$app")
  if run_mfe "$app"; then
    echo "${GREEN}✓ $app${RESET}"
  else
    echo "${RED}✗ $app${RESET}"
    FAILED+=("$app")
  fi
done

echo
if [[ ${#RAN[@]} -eq 0 ]]; then
  echo "${YELLOW}No MFEs to test$( $AFFECTED && echo ' (no staged frontend changes)').${RESET}"
  exit 0
fi
echo "${BLUE}--- Frontend test summary (${#RAN[@]} suite(s)) ---${RESET}"
if [[ ${#FAILED[@]} -eq 0 ]]; then
  echo "${GREEN}✓ all frontend suites passed${RESET}"
  exit 0
fi
echo "${RED}✗ ${#FAILED[@]} failed:${RESET}"
for f in "${FAILED[@]}"; do echo "  - $f"; done
exit 1
