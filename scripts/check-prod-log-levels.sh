#!/bin/bash

##############################################################################
# Production Log-Level Guard  (Scalability P1)
#
# Two checks:
#
#   CHECK 1 - No verbose levels in prod files.
#     Fails if any application-prod.{yml,yaml,properties} (incl. sub-profile
#     variants such as application-prod-eu.yml) declares a DEBUG or TRACE log
#     level. Case-insensitive and quote-tolerant (Spring parses levels
#     case-insensitively, and YAML/properties allow quoted values).
#
#   CHECK 2 - Base->prod exact-key drift.
#     Logback/Spring apply logging.level.* per EXACT logger name; a logger
#     with its own explicit level does NOT inherit from a same-prefix
#     ancestor. So a DEBUG/TRACE logger declared in an unconditional base
#     application.{yml,properties} stays verbose under the prod profile unless
#     that EXACT key is overridden in the module's application-prod.* file.
#     This check fails if any such base key lacks an exact-key prod override,
#     catching future drift (e.g. a new specific DEBUG logger added to base).
#
# Note: CHECK 2 scans the whole base file (all YAML documents), which is a
# safe over-approximation - it may require a prod override for a key that is
# only DEBUG in a non-prod document, but that override is harmless.
#
# Usage: scripts/check-prod-log-levels.sh
# Exit:  0 = clean, 1 = at least one violation.
##############################################################################

set -euo pipefail

GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Matches a DEBUG/TRACE value after ':' (YAML) or '=' (properties), tolerating
# an optional surrounding quote. Used with `grep -iE` for case-insensitivity.
Q="[\"']"
LEVEL_RE="(:|=)[[:space:]]*${Q}?(DEBUG|TRACE)${Q}?([[:space:]]|\$)"

violations=0

# ---------------------------------------------------------------------------
# CHECK 1 - production profile files must not contain DEBUG/TRACE
# ---------------------------------------------------------------------------
echo -e "${BLUE}[1/2] Scanning production profiles for DEBUG/TRACE levels...${NC}"

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

for file in "${PROD_FILES[@]}"; do
  rel="${file#"$REPO_ROOT"/}"
  # Strip comments so comment text mentioning DEBUG/TRACE never trips the guard.
  hits="$(sed -E 's/#.*$//' "$file" | grep -niE "$LEVEL_RE" || true)"
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

# ---------------------------------------------------------------------------
# CHECK 2 - every base DEBUG/TRACE logger key has an exact-key prod override
# ---------------------------------------------------------------------------
echo ""
echo -e "${BLUE}[2/2] Checking base -> prod exact-key overrides...${NC}"

# Extract logging.level logger keys whose value is DEBUG/TRACE from a base file.
# YAML lines:        '    com.ecommerce.foo: DEBUG'  -> 'com.ecommerce.foo'
# properties lines:  'logging.level.com.x=DEBUG'     -> 'com.x'
extract_base_debug_keys() {
  local bf="$1"
  # `|| true` so a file with no DEBUG/TRACE keys (grep exit 1) does not trip
  # `set -o pipefail` + `set -e`.
  { sed -E 's/#.*$//' "$bf" \
    | grep -iE "$LEVEL_RE" \
    | sed -E 's/^[[:space:]]*logging\.level\.//; s/^[[:space:]]*//; s/[[:space:]]*[:=].*$//' \
    | sort -u; } || true
}

# True if logger $key has an exact-key entry in any prod file in $dir.
prod_has_key() {
  local dir="$1" key="$2" esc pf
  esc="${key//./\\.}"
  for pf in "$dir"/application-prod.yml "$dir"/application-prod.yaml \
            "$dir"/application-prod.properties "$dir"/application-prod-*.yml \
            "$dir"/application-prod-*.yaml "$dir"/application-prod-*.properties; do
    [ -f "$pf" ] || continue
    # YAML form '<key>:' or properties form 'logging.level.<key>='.
    if sed -E 's/#.*$//' "$pf" \
        | grep -qE "(^[[:space:]]*|logging\.level\.)${esc}[[:space:]]*[:=]"; then
      return 0
    fi
  done
  return 1
}

while IFS= read -r base_file; do
  keys="$(extract_base_debug_keys "$base_file")"
  [ -z "$keys" ] && continue
  dir="$(dirname "$base_file")"
  rel="${base_file#"$REPO_ROOT"/}"
  module_missing=""
  while IFS= read -r key; do
    [ -z "$key" ] && continue
    if ! prod_has_key "$dir" "$key"; then
      module_missing+="    $key"$'\n'
    fi
  done <<< "$keys"
  if [ -n "$module_missing" ]; then
    echo -e "${RED}FAIL${NC} $rel - base DEBUG/TRACE keys with no exact-key prod override:"
    printf '%s' "$module_missing"
    violations=$((violations + 1))
  else
    echo -e "${GREEN}PASS${NC} $rel"
  fi
done < <(find "$REPO_ROOT" \
  \( -path '*/target/*' -o -path '*/node_modules/*' -o -path '*/build/*' -o -path '*/test/*' \) -prune -o \
  -type f -path '*/src/main/resources/*' \
  \( -name 'application.yml' -o -name 'application.yaml' -o -name 'application.properties' \) \
  -print | sort)

# ---------------------------------------------------------------------------
echo ""
if [ "$violations" -gt 0 ]; then
  echo -e "${RED}$violations violation(s) found.${NC}"
  echo -e "${RED}Lower prod levels to INFO/WARN and add exact-key overrides for every${NC}"
  echo -e "${RED}base DEBUG/TRACE logger. Verbose logging must not reach production.${NC}"
  exit 1
fi

echo -e "${GREEN}All ${#PROD_FILES[@]} prod file(s) clean and all base DEBUG/TRACE keys overridden.${NC}"
