#!/bin/bash

##############################################################################
# Dockerfile Reactor-POM Drift Guard  (Build-pipeline integrity)
#
# The root pom.xml is a Maven aggregator: it parses EVERY module listed in its
# <modules> block to build the reactor model, even when a single module is
# targeted with `-pl ... -am`. A hermetic multi-stage Dockerfile that COPYs the
# aggregator root pom.xml must therefore stage a pom.xml for EVERY reactor
# module - otherwise the in-container build aborts before it starts with:
#
#     Child module <path> of <root>/pom.xml does not exist
#
# This drift is invisible in a normal `mvn` build (all module dirs exist on
# disk) and only surfaces inside the Docker build context. It is introduced
# whenever a new module is added to root pom.xml but its COPY line is not added
# to the Dockerfiles, or when a COPY line is accidentally removed.
#
# This guard compares each reactor Dockerfile's COPY'd module-pom list against
# root pom.xml's <modules> and fails on any missing (or stale) module pom.
#
# --- Scope / enforcement -----------------------------------------------------
# A Dockerfile is scanned when it COPYs the aggregator root pom.xml. Runtime-
# only Dockerfiles (*.runtime, *.native) ship a pre-built JAR and never build
# the reactor, so they are ignored.
#
# A scanned Dockerfile is ENFORCED (drift => hard failure) only when it carries
# the opt-in marker line:
#
#     # reactor-pom-check: enforce
#
# Non-enforced Dockerfiles that copy the root pom but stage an incomplete list
# are reported as WARNINGS (advisory, non-fatal) so latent breakage stays
# visible without red-walling the required CI gate. Add the marker to a
# Dockerfile once its reactor-pom list is complete to lock it in.
#
# Usage: scripts/check-dockerfile-reactor-poms.sh
# Exit:  0 = all enforced Dockerfiles in sync, 1 = drift in an enforced file.
##############################################################################

set -euo pipefail

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ROOT_POM="$REPO_ROOT/pom.xml"
MARKER='# reactor-pom-check: enforce'

if [ ! -f "$ROOT_POM" ]; then
  echo -e "${RED}Root pom.xml not found at $ROOT_POM - guard cannot run.${NC}"
  exit 1
fi

# ---------------------------------------------------------------------------
# Reactor modules declared in root pom.xml <modules> (commented-out entries,
# e.g. '<!-- <module>integration-tests</module> -->', are excluded).
# ---------------------------------------------------------------------------
declare -a MODULES=()
while IFS= read -r module; do
  MODULES+=("$module")
done < <(grep '<module>' "$ROOT_POM" \
  | grep -v '<!--' \
  | grep -oE '<module>[^<]+</module>' \
  | sed -E 's:</?module>::g' \
  | sed -E 's:[[:space:]]+::g' \
  | sort -u)

if [ "${#MODULES[@]}" -eq 0 ]; then
  echo -e "${RED}No <module> entries parsed from root pom.xml - guard cannot verify anything.${NC}"
  exit 1
fi

echo -e "${BLUE}Root pom.xml declares ${#MODULES[@]} reactor module(s).${NC}"

# ---------------------------------------------------------------------------
# Discover candidate Dockerfiles: any Dockerfile (excluding runtime/native
# variants) that COPYs the aggregator root pom.xml.
# ---------------------------------------------------------------------------
declare -a DOCKERFILES=()
while IFS= read -r df; do
  DOCKERFILES+=("$df")
done < <(find "$REPO_ROOT" \
  \( -path '*/target/*' -o -path '*/node_modules/*' -o -path '*/build/*' -o -path '*/.claude/*' \) -prune -o \
  -type f \( -name 'Dockerfile' -o -name 'Dockerfile.*' \) \
  ! -name 'Dockerfile.runtime' ! -name 'Dockerfile.native' ! -name 'Dockerfile.dockerignore' \
  -print | sort)

# Extract the module directories whose pom.xml a Dockerfile stages, i.e. the
# source path of `COPY <dir>/pom.xml ...` lines. The bare `COPY pom.xml` (the
# aggregator itself) has no directory component and is intentionally excluded.
copied_module_poms() {
  local df="$1"
  # `|| true` so a Dockerfile with no `COPY <dir>/pom.xml` lines (grep exit 1)
  # does not trip `set -o pipefail` + `set -e`.
  { sed -E 's/#.*$//' "$df" \
    | grep -E '^[[:space:]]*COPY[[:space:]]+[^[:space:]]+/pom\.xml([[:space:]]|$)' \
    | sed -E 's/^[[:space:]]*COPY[[:space:]]+([^[:space:]]+)\/pom\.xml.*/\1/' \
    | sed -E 's:^\./::' \
    | sort -u; } || true
}

copies_root_pom() {
  sed -E 's/#.*$//' "$1" \
    | grep -qE '^[[:space:]]*COPY[[:space:]]+pom\.xml([[:space:]]|$)'
}

is_enforced() {
  grep -qF "$MARKER" "$1"
}

violations=0
warnings=0
enforced_seen=0

for df in "${DOCKERFILES[@]}"; do
  copies_root_pom "$df" || continue
  rel="${df#"$REPO_ROOT"/}"

  copied="$(copied_module_poms "$df")"

  # Modules in root pom that this Dockerfile fails to stage.
  missing=""
  for m in "${MODULES[@]}"; do
    if ! grep -qxF "$m" <<< "$copied"; then
      missing+="    missing COPY: $m/pom.xml"$'\n'
    fi
  done

  # Module poms staged by the Dockerfile that no longer exist in root <modules>.
  stale=""
  while IFS= read -r c; do
    [ -z "$c" ] && continue
    if ! printf '%s\n' "${MODULES[@]}" | grep -qxF "$c"; then
      stale+="    stale COPY (not in root <modules>): $c/pom.xml"$'\n'
    fi
  done <<< "$copied"

  if is_enforced "$df"; then
    enforced_seen=$((enforced_seen + 1))
    if [ -n "$missing" ] || [ -n "$stale" ]; then
      echo -e "${RED}FAIL${NC} $rel ${BLUE}(enforced)${NC}"
      [ -n "$missing" ] && printf '%s' "$missing"
      [ -n "$stale" ] && printf '%s' "$stale"
      violations=$((violations + 1))
    else
      echo -e "${GREEN}PASS${NC} $rel ${BLUE}(enforced, ${#MODULES[@]}/${#MODULES[@]} module poms)${NC}"
    fi
  else
    if [ -n "$missing" ] || [ -n "$stale" ]; then
      echo -e "${YELLOW}WARN${NC} $rel (copies root pom but is not enforced; incomplete reactor pom list)"
      [ -n "$missing" ] && printf '%s' "$missing"
      [ -n "$stale" ] && printf '%s' "$stale"
      warnings=$((warnings + 1))
    else
      echo -e "${GREEN}PASS${NC} $rel (complete; add '$MARKER' to enforce)"
    fi
  fi
done

echo ""
if [ "$enforced_seen" -eq 0 ]; then
  echo -e "${RED}No enforced Dockerfiles found (marker '$MARKER').${NC}"
  echo -e "${RED}At least one hermetic reactor Dockerfile must opt in.${NC}"
  exit 1
fi

if [ "$warnings" -gt 0 ]; then
  echo -e "${YELLOW}$warnings non-enforced Dockerfile(s) have an incomplete reactor pom list (advisory).${NC}"
fi

if [ "$violations" -gt 0 ]; then
  echo -e "${RED}$violations enforced Dockerfile(s) out of sync with root pom.xml <modules>.${NC}"
  echo -e "${RED}Add/remove the COPY <module>/pom.xml lines so they match root <modules>,${NC}"
  echo -e "${RED}otherwise the hermetic Docker build aborts with 'Child module ... does not exist'.${NC}"
  exit 1
fi

echo -e "${GREEN}All $enforced_seen enforced Dockerfile(s) stage every reactor module pom.${NC}"
