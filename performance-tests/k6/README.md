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

- Provide one or more pre-minted bearer tokens to run the full checkout flow:
  - **`AUTH_TOKENS`** — a comma/whitespace-separated **pool** of tokens (preferred).
  - **`AUTH_TOKENS_FILE`** — path to a file with one token per line (loaded via
    k6 `SharedArray`).
  - **`AUTH_TOKEN`** — a single token (fine for `smoke`; discouraged for
    `load`/`stress`, see the caveat below).
- Omit all of them to run a **browse-only, unauthenticated** load test. This is
  fully supported and useful on its own.

Tokens in the pool are assigned to VUs **round-robin**, so VU 1 uses token 1,
VU 2 uses token 2, and so on (wrapping when there are more VUs than tokens).

> Route nuance: `GET /api/products` (unversioned) is public, but
> `GET /api/v1/products` requires auth. The suite automatically uses the
> unversioned catalog path when unauthenticated so browse-only runs work.

Tokens are only ever read from the environment/file. **Never** hard-code or
commit them.

### CRITICAL: rate limiting & the single-token trap

The gateway rate-limits by **principal** — its `@Primary combinedKeyResolver`
keys on the JWT `sub` (replenishRate **100/s**, burst **200**, with **no**
per-route override). Consequences for a load/stress run driven by **one** token:

1. **Every VU is the same principal**, so they all share one rate-limit bucket.
   Past ~100–200 req/s you measure `429`s from the throttle, **not** service
   capacity.
2. **Every VU mutates one shared cart row**, creating artificial contention that
   doesn't exist with real, distinct users.

Therefore **`load`/`stress` numbers taken with a single token are INVALID for
capacity planning.** The suite prints a loud warning in this case. Supply a pool
(`AUTH_TOKENS`/`AUTH_TOKENS_FILE`) sized at or above your target VU count — each
token must be a distinct subject — for trustworthy results.

### Notification fan-out

`createOrder` publishes an order-created event, which `notification-service`
may turn into email/SMS. The suite uses **per-VU unique**, invalid addresses
(`loadtest+vu<N>@example.invalid`) to avoid hammering a single inbox. **Before
any non-smoke run, confirm `notification-service` is sandboxed** (dev mail sink
/ provider disabled) so a load run does not attempt to send thousands of real
messages.

## Configuration (all via env vars)

| Var | Default | Description |
|-----|---------|-------------|
| `BASE_URL` | `http://localhost:8080` | Gateway base URL |
| `PROFILE` | `smoke` | `smoke` \| `load` \| `stress` |
| `VUS` | per-profile | Override VU count (constant) / ramp target |
| `DURATION` | per-profile | Override total scenario duration (e.g. `2m`, `30s`) |
| `AUTH_TOKENS` | _(unset)_ | Comma/whitespace-separated token pool (preferred) |
| `AUTH_TOKENS_FILE` | _(unset)_ | Path to a file with one token per line |
| `AUTH_TOKEN` | _(unset)_ | Single bearer token (smoke only; see caveat) |
| `USER_ID` | decoded from token / `k6-load-test-user` | Base for synthetic order userId |
| `API_VERSION` | `v1` | `v1` (canonical) or `none` (legacy unversioned routes) |
| `THINK_TIME` | `1` | Seconds of sleep between iterations |
| `STEP_PAUSE` | `0.3` | Seconds of sleep between checkout steps |
| `FALLBACK_PRODUCT_ID` | `1` | Product id used only if the catalog page is empty |

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

Each step is also tagged (`browse_products`, `product_detail`, `add_to_cart`,
`create_order`, `payment_intent`) and pinned with a per-step
`http_req_duration{name:...}` threshold, so a regression surfaces on the exact
step responsible rather than only in the aggregate trend.

A run **fails** (non-zero exit) if any threshold is breached — this is what makes
it usable as a CI gate.

## Running

### Docker (no local install)

The image tag is pinned for reproducibility (matches the CI workflow).

```bash
docker run --rm -i \
  -e BASE_URL="http://host.docker.internal:8080" \
  -e PROFILE=smoke \
  -v "$PWD/performance-tests/k6:/scripts" \
  grafana/k6:0.52.0 run /scripts/scenarios/golden-path.js
```

> On Linux without `host.docker.internal`, add `--add-host=host.docker.internal:host-gateway`
> or use `--network=host` and `BASE_URL=http://localhost:8080`.

Authenticated + load profile, with a **token pool** and results export:

```bash
docker run --rm -i \
  -e BASE_URL="https://staging-gateway.example.com" \
  -e PROFILE=load \
  -e AUTH_TOKENS="$TOKEN_1,$TOKEN_2,$TOKEN_3,...,$TOKEN_N" \
  -v "$PWD/performance-tests/k6:/scripts" \
  -v "$PWD/k6-results:/results" \
  grafana/k6:0.52.0 run \
    --summary-export=/results/summary.json \
    --out json=/results/results.json \
    /scripts/scenarios/golden-path.js
```

### Local binary

```bash
# macOS: brew install k6   |   see https://k6.io/docs/get-started/installation/
cd performance-tests/k6

BASE_URL=http://localhost:8080 PROFILE=smoke k6 run scenarios/golden-path.js

# Full checkout against staging, load profile, token pool from a file:
BASE_URL=https://staging-gateway.example.com PROFILE=load \
  AUTH_TOKENS_FILE=./tokens.txt \
  k6 run --summary-export=summary.json scenarios/golden-path.js

# Ad-hoc override — 50 VUs for 30s regardless of profile shape:
PROFILE=smoke VUS=50 DURATION=30s k6 run scenarios/golden-path.js
```

### Syntax-validate without a live backend

```bash
k6 inspect scenarios/golden-path.js          # k6 binary
# or with docker:
docker run --rm -v "$PWD/performance-tests/k6:/scripts" \
  grafana/k6:0.52.0 inspect /scripts/scenarios/golden-path.js
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
- **checkout thresholds absent from output** → no token was set, so only the
  browse flow ran (expected for unauthenticated runs).
- **checkout `429`s / errors climb immediately under load** → likely a
  single-token run hitting the per-principal rate limit; use a token pool.

### Exported results

Pass `--summary-export=summary.json` for the aggregated metrics/thresholds and
`--out json=results.json` for the raw per-request stream (large under
load/stress). The CI workflow writes both to `k6-results/` and uploads them.

## CI

`.github/workflows/perf-smoke.yml` runs the **smoke** profile on manual
dispatch (`workflow_dispatch`) against a `base_url` input, exports
`summary.json` + `results.json`, and uploads them as a build artifact (kept
30 days, uploaded even when thresholds fail). It is intentionally **not** wired
into every PR because the platform isn't running in CI. A nightly cron stanza is
included but commented out — enable it only once a persistent staging
environment exists and its URL (`vars.STAGING_GATEWAY_URL`) and, for the
checkout flow, a token pool (`secrets.PERF_AUTH_TOKENS`) are configured.
