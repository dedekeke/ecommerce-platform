#!/bin/bash

##############################################################################
# Production Log-Level Guard
#
# Fails if any production Spring profile config (application-prod.yml /
# application-prod.yaml / application-prod.properties, incl. sub-profile
# variants such as application-prod-eu.yml) declares a DEBUG or TRACE log
# level. Verbose SQL / parameter-binding logging in production is a
# scalability and security risk (Scalability P1).
#
# Usage: scripts/check-prod-log-levels.sh
# Exit:  0 = clean, 1 = at least one DEBUG/TRACE level found.
##############################################################################

set -euo pipefail

GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

# Resolve repo root as the parent of this script's directory so the guard
# works regardless of the caller's current working directory.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo -e "${BLUE}Scanning production profiles for DEBUG/TRACE log levels...${NC}"

# Collect prod profile config files, skipping build output and dependencies.
# Populated via a while-read loop (portable to bash 3.2, e.g. stock macOS).
PROD_FILES=()
while IFS= read -r prod_file; do
  PROD_FILES+=("$prod_file")
done < <(find "$REPO_ROOT" \
  \( -path '*/target/*' -o -path '*/node_modules/*' -o -path '*/build/*' \) -prune -o \
  -type f \( \
    -name 'application-prod.yml' -o \
    -name 'application-prod.yaml' -o \
    -name 'application-prod.properties' -o \
    -name 'application-prod-*.yml' -o \
    -name 'application-prod-*.yaml' -o \
    -name 'application-prod-*.properties' \
  \) -print | sort)

if [ "${#PROD_FILES[@]}" -eq 0 ]; then
  echo -e "${RED}No application-prod.* files found - guard cannot verify anything.${NC}"
  exit 1
fi

violations=0
for file in "${PROD_FILES[@]}"; do
  rel="${file#"$REPO_ROOT"/}"
  # Strip comments (# to end-of-line) before matching so comment text that
  # merely mentions DEBUG/TRACE never trips the guard. Match a DEBUG/TRACE
  # value assigned after ':' (YAML) or '=' (properties).
  hits="$(sed -E 's/#.*$//' "$file" \
    | grep -nE '(:|=)[[:space:]]*(DEBUG|TRACE)([[:space:]]|$)' || true)"
  if [ -n "$hits" ]; then
    echo -e "${RED}FAIL${NC} $rel"
    while IFS= read -r line; do
      echo "    $line"
    done <<< "$hits"
    violations=$((violations + 1))
  else
    echo -e "${GREEN}PASS${NC} $rel"
  fi
done

echo ""
if [ "$violations" -gt 0 ]; then
  echo -e "${RED}$violations production profile file(s) contain DEBUG/TRACE log levels.${NC}"
  echo -e "${RED}Lower them to INFO/WARN. Verbose logging must not reach production.${NC}"
  exit 1
fi

echo -e "${GREEN}All ${#PROD_FILES[@]} production profile file(s) are clean.${NC}"
