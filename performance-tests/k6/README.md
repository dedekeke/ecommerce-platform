# k6 Load & Performance Suite

Executable load tests for the e-commerce platform, driving the **golden path**
through the `api-gateway`:

```
browse catalog → product detail → add to cart → create order → payment intent
```

The suite is written in [k6](https://k6.io) (JavaScript). It runs against a
live gateway (local, staging, or ephemeral env) — it does **not** spin up the
platform itself.

## Layout

```
performance-tests/k6/
├── scenarios/
│   └── golden-path.js     # entry point: browse (+ checkout when authed)
├── lib/
│   ├── config.js          # profiles, env parsing, scenarios, thresholds (SLOs)
│   ├── auth.js            # AUTH_TOKEN handling + graceful browse-only fallback
│   ├── checkout.js        # per-step flow helpers mapped to gateway routes
│   └── metrics.js         # shared custom trends/rates the thresholds key off
└── README.md
```

## Prerequisites (what must be up)

The scripts hit the gateway; the gateway proxies downstream services. For a run
to be meaningful the following must be reachable:

| Flow | Services required |
|------|-------------------|
| Browse-only (no token) | `api-gateway`, `product-service`, Eureka discovery |
| Full checkout (with token) | above **plus** `cart-service`, `order-service`, `payment-service`, Redis (rate-limiter), and a valid JWT the gateway accepts |

Bring the platform up with the repo's usual tooling (e.g. `docker compose up` /
the service start scripts) before pointing k6 at it.

## Authentication

The gateway is a **JWT resource server**. This suite does *not* perform an
Auth0 client-credentials handshake — those credentials aren't available in every
environment and must never be committed. Instead:

- Export a pre-minted bearer token via **`AUTH_TOKEN`** to run the full
  checkout flow.
- Omit `AUTH_TOKEN` to run a **browse-only, unauthenticated** load test. This is
  fully supported and useful on its own.

> Route nuance: `GET /api/products` (unversioned) is public, but
> `GET /api/v1/products` requires auth. The suite automatically uses the
> unversioned catalog path when unauthenticated so browse-only runs work.

The token is only ever read from the environment. **Never** hard-code it or
commit it.

## Configuration (all via env vars)

| Var | Default | Description |
|-----|---------|-------------|
| `BASE_URL` | `http://localhost:8080` | Gateway base URL |
| `PROFILE` | `smoke` | `smoke` \| `load` \| `stress` |
| `VUS` | per-profile | Override VU count (constant) / ramp target |
| `DURATION` | per-profile | Override total scenario duration (e.g. `2m`, `30s`) |
| `AUTH_TOKEN` | _(unset)_ | Bearer token; enables checkout flow |
| `USER_ID` | decoded from token / `k6-load-test-user` | Order userId override |
| `API_VERSION` | `v1` | `v1` (canonical) or `none` (legacy unversioned routes) |
| `THINK_TIME` | `1` | Seconds of sleep between iterations |

### Profiles

| Profile | Shape | VUs | Duration |
|---------|-------|-----|----------|
| `smoke` | constant | 5 | 1m |
| `load` | ramp up → hold → down | → 200 | ~9m |
| `stress` | ramp up → hold → down | → 1000 | ~23m |

### Thresholds (SLOs — constant across profiles)

| Metric | Threshold |
|--------|-----------|
| `browse_latency` (catalog list + detail) | p95 < 500ms |
| `checkout_latency` (cart + order + payment) | p95 < 1500ms |
| `errors` (business error rate) | rate < 1% |
| `http_req_failed` (transport failures) | rate < 1% |

A run **fails** (non-zero exit) if any threshold is breached — this is what makes
it usable as a CI gate.

## Running

### Docker (no local install)

```bash
docker run --rm -i \
  -e BASE_URL="http://host.docker.internal:8080" \
  -e PROFILE=smoke \
  -v "$PWD/performance-tests/k6:/scripts" \
  grafana/k6 run /scripts/scenarios/golden-path.js
```

> On Linux without `host.docker.internal`, add `--add-host=host.docker.internal:host-gateway`
> or use `--network=host` and `BASE_URL=http://localhost:8080`.

Authenticated + load profile:

```bash
docker run --rm -i \
  -e BASE_URL="https://staging-gateway.example.com" \
  -e PROFILE=load \
  -e AUTH_TOKEN="$MY_BEARER_TOKEN" \
  -v "$PWD/performance-tests/k6:/scripts" \
  grafana/k6 run /scripts/scenarios/golden-path.js
```

### Local binary

```bash
# macOS: brew install k6   |   see https://k6.io/docs/get-started/installation/
cd performance-tests/k6

BASE_URL=http://localhost:8080 PROFILE=smoke k6 run scenarios/golden-path.js

# Full checkout against staging, load profile:
BASE_URL=https://staging-gateway.example.com PROFILE=load \
  AUTH_TOKEN="$MY_BEARER_TOKEN" k6 run scenarios/golden-path.js

# Ad-hoc override — 50 VUs for 30s regardless of profile shape:
PROFILE=smoke VUS=50 DURATION=30s k6 run scenarios/golden-path.js
```

### Syntax-validate without a live backend

```bash
k6 inspect scenarios/golden-path.js          # k6 binary
# or with docker:
docker run --rm -v "$PWD/performance-tests/k6:/scripts" \
  grafana/k6 inspect /scripts/scenarios/golden-path.js
```

## Interpreting results

k6 prints a summary at the end. Key lines:

- **`browse_latency` / `checkout_latency`** — look at `p(95)`. Green if under
  the SLOs above. The `✓`/`✗` next to `THRESHOLDS` tells you pass/fail.
- **`errors`** — business errors (bad status / missing fields). Should be ~0%.
- **`http_req_failed`** — transport failures (timeouts, resets). Rising values
  under `load`/`stress` indicate saturation or a downstream falling over.
- **`iterations` / `vus`** — throughput and concurrency reached.

Typical reading:

- **smoke fails on `browse_latency`** → gateway/product-service is slow even at
  5 VUs; likely a cold cache, N+1 query, or missing index — investigate before
  scaling up.
- **load passes, stress fails on `http_req_failed`** → you found the knee of the
  curve; note the VU count where errors start climbing as the current capacity
  ceiling.
- **checkout thresholds absent from output** → no `AUTH_TOKEN` was set, so only
  the browse flow ran (expected for unauthenticated runs).

## CI

`.github/workflows/perf-smoke.yml` runs the **smoke** profile on manual
dispatch (`workflow_dispatch`) against a `base_url` input. It is intentionally
**not** wired into every PR because the platform isn't running in CI. A nightly
cron stanza is included but commented out — enable it only once a persistent
staging environment (and its URL/secret) exist.
