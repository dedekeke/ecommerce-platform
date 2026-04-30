# OWASP ZAP Security Scan

## Overview

The `scripts/security-scan.sh` script runs an OWASP ZAP passive baseline scan against the API gateway. It uses the official `owasp/zap2docker-stable` Docker image and generates an HTML report in `security-reports/`.

Reports are gitignored so they are never committed to the repository.

## Prerequisites

- Docker is installed and the Docker daemon is running
- The API gateway is accessible at `http://localhost:8080` (or set `TARGET_URL`)

## Running the Scan

```bash
./scripts/security-scan.sh
```

Against a different target:

```bash
TARGET_URL=http://staging.example.com ./scripts/security-scan.sh
```

## What It Does

1. Pulls `owasp/zap2docker-stable` (or uses the locally cached layer).
2. Runs `zap-baseline.py` in passive mode — no active attacks are sent. The spider crawls the target for up to 5 minutes.
3. Writes an HTML report to `security-reports/zap-{YYYYMMDD-HHMMSS}.html`.
4. Exits with code `1` if any **HIGH** severity finding is detected.
5. Exits with code `0` if only MEDIUM/LOW/INFO findings are present.

## Exit Codes

| Code | Meaning |
|------|---------|
| 0 | Clean — no HIGH severity findings |
| 1 | HIGH severity finding detected — investigate before merging |
| 2 | Script setup error (Docker not running, etc.) |

## Triaging Findings

1. Open the HTML report in `security-reports/`.
2. Identify HIGH and MEDIUM findings.
3. For each finding, determine if it is a true positive or a known acceptable deviation.
4. True positives must be remediated before the PR can merge.
5. Acceptable deviations (e.g., intentional CORS policy, Dev-only endpoints) should be documented in this file under the **Known Acceptable Findings** section below.

## Known Acceptable Findings

Document any allow-listed findings here with the ZAP alert ID, the reason it is acceptable, and the date it was reviewed.

| Alert ID | Name | Reason | Reviewed |
|----------|------|--------|----------|
| — | — | — | — |

## CI Integration

The scan is scripted but not wired to CI yet. When you are ready to add it:

1. Add a `security` job in `.github/workflows/quality.yml` that calls `./scripts/security-scan.sh`.
2. Ensure the API gateway is started as a `service` container in the job.
3. Set `continue-on-error: false` so HIGH findings block the merge.
