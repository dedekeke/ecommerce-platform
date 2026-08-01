#!/usr/bin/env bash
# OWASP ZAP passive baseline scan against the API gateway.
#
# Prerequisites:
#   - Docker is running
#   - API gateway is accessible at $TARGET_URL (default: http://localhost:8080)
#
# Usage:
#   ./scripts/security-scan.sh
#   TARGET_URL=http://staging.example.com ./scripts/security-scan.sh
#
# Exit codes:
#   0 — no HIGH severity findings
#   1 — one or more HIGH severity findings detected (pipeline gate)
#   2 — script setup error (Docker not available, missing args, etc.)

set -euo pipefail

TARGET_URL="${TARGET_URL:-http://localhost:8080}"
# owasp/zap2docker-stable on Docker Hub is deprecated/unmaintained (moved 2023).
ZAP_IMAGE="${ZAP_IMAGE:-ghcr.io/zaproxy/zaproxy:stable}"
REPORT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/security-reports"
DATE_TAG="$(date +%Y%m%d-%H%M%S)"
REPORT_FILE="zap-${DATE_TAG}.html"
CONTAINER_REPORT="/zap/wrk/${REPORT_FILE}"

if ! command -v docker &>/dev/null; then
  echo "[ERROR] Docker is not installed or not on PATH." >&2
  exit 2
fi

if ! docker info &>/dev/null; then
  echo "[ERROR] Docker daemon is not running." >&2
  exit 2
fi

mkdir -p "${REPORT_DIR}"

echo "[INFO] Pulling ZAP image: ${ZAP_IMAGE}"
docker pull "${ZAP_IMAGE}"

echo "[INFO] Starting ZAP baseline scan against: ${TARGET_URL}"
echo "[INFO] Report will be written to: ${REPORT_DIR}/${REPORT_FILE}"

# The zap-baseline.py script exits with:
#   0 — no alerts
#   1 — WARN alerts only
#   2 — FAIL alerts (HIGH / MEDIUM depending on config)
#   3 — internal ZAP error
#
# We pass -l PASS so that HIGH alerts cause a non-zero exit.
# The -I flag ignores only INFORMATIONAL alerts.
# The -m 5 limits the spider duration to 5 minutes.
ZAP_EXIT=0
docker run --rm \
  --network host \
  -v "${REPORT_DIR}:/zap/wrk:rw" \
  "${ZAP_IMAGE}" \
  zap-baseline.py \
    -t "${TARGET_URL}" \
    -r "${REPORT_FILE}" \
    -m 5 \
    -l PASS \
    -I \
    --auto \
    -z "-config scanner.threadPerHost=4" \
  || ZAP_EXIT=$?

echo "[INFO] ZAP scan completed. Exit code: ${ZAP_EXIT}"
echo "[INFO] HTML report: ${REPORT_DIR}/${REPORT_FILE}"

# ZAP exit code 2 means FAIL-level alerts were raised (HIGH by our config).
if [ "${ZAP_EXIT}" -eq 2 ]; then
  echo "[FAIL] HIGH severity findings detected. Review ${REPORT_DIR}/${REPORT_FILE}" >&2
  exit 1
fi

if [ "${ZAP_EXIT}" -ge 3 ]; then
  echo "[ERROR] ZAP encountered an internal error (exit code ${ZAP_EXIT})." >&2
  exit 2
fi

echo "[PASS] No HIGH severity findings."
exit 0
