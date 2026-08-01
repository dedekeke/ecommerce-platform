# E-Commerce Platform - TODO & Progress Tracker

> Last updated: 2026-07-15 — hardening continues: PRs #110–#120 merged (trace sampling, e2e mock auth, listing cache, catalog PATCH scoping, category routing, CI dockerfile guard, loyalty dedup, media IDOR); #112/#116/#121/#122 in review.
> Previous update: 2026-07-14 — Hardening sprint merged: PRs #95–#109.

---

## Current Status: Post-Plan Hardening & Operations

The 10-week plan (all MFEs, production readiness, documentation) is complete.
Current work is iterative hardening driven by review findings and
[docs/SCALING_AND_IMPROVEMENTS.md](docs/SCALING_AND_IMPROVEMENTS.md) — closing
the gap to the "1000 concurrent users, production-ready" target. See
[plan.md](plan.md) for the status summary and remaining-gaps breakdown.

---

## In Review (open PRs)

- [ ] #112 — replica-safety for scheduled jobs: outbox SKIP LOCKED, dedicated scheduler pool, ShedLock, order-service Flyway/validate
- [ ] #116 — align orders_archive to postal_code so the archival job can run
- [ ] #121 — checkout-mfe: remove raw-PAN payment form, Stripe Elements only
- [ ] #122 — order-service: wire REST checkout to the real saga + Idempotency-Key

---

## Backlog

### Security
- [ ] order-service opaque-id enumeration: normalize 403→404 for non-admin callers on `GET /orders/number/{n}` and `GET /returns/{rmaId}`
- [ ] product-service internal-caller scopes: decide service-to-service scope for legitimate internal stock PATCHes now that #114 requires SCOPE_admin
- [ ] Add NVD_API_KEY repo secret → re-enable OWASP dependency-check CVSS≥7 gate, triage CVEs, populate suppressions
- [ ] Re-run OWASP ZAP baseline against staging with `SECURITY_ENABLED=true` (initial scan: `docs/SECURITY_SCAN.md`)

### Scalability
- [ ] Service-tier Dockerfile hermetic builds: all 10 service Dockerfiles copy the root pom but stage only 3 module poms and miss common-logging — apply the #97/#98 pattern, flip the #117 drift guard from advisory to enforce per file fixed
- [ ] Connection budget: add rolling-deploy surge term (maxSurge pods can transiently exceed the 200-connection ceiling) — pad POSTGRES_MAX_CONNECTIONS or document deploy sequencing (see `docs/db-connection-budget.md`)
- [ ] Shell-app prod build: env-based federation remote URLs (currently hardcoded localhost:5001-5005), enable minification, add CI check
- [ ] Infra SPOFs: single Zipkin + single-node Elasticsearch backend in prod compose
- [ ] Scaling roadmap items from `docs/SCALING_AND_IMPROVEMENTS.md`: StructuredTaskScope fan-out, Debezium CDC for the outbox, SLOs/error budgets, read replicas, CDN

### Tech Debt
- [ ] shell-app `PaginatedResponse<T>` phantom `page`-vs-`number` field; product-catalog-mfe `getFeaturedProducts` must unwrap the Page envelope (+ fix its MSW mock)
- [ ] review-service + recommendation-service: audit for raw `Page<T>` returns (apply the #101/#102 PageResponse envelope) and add both to the CD workflow matrix
- [ ] common-logging umbrella `<logger name="com.ecommerce">` is ineffective (exact-key resolution) — pin keys, remove, or replace with a prod-profile smoke test; DRY the 15 prod-logging fragment copies
- [ ] Real-Auth0 accessor race: checkout-mfe reads `window.__getAuthUserId` synchronously before the shell's passive effect installs it — mock path fixed in #111; consider useSyncExternalStore or an auth-ready event for the real path
- [ ] Migrate feature flags to Unleash when active flag count exceeds ~10
- [ ] Frontend beautification (progressive, low priority) — dark mode, reduced-motion, micro-interactions per [docs/frontend-design-brief.md](docs/frontend-design-brief.md)

### Verification (blocked on Docker daemon / staging)
- [ ] #112 merge gate: Testcontainers Postgres boot check of order-service with `flyway.enabled=true` + `ddl-auto=validate` (full V1..V10 chain)
- [ ] #116 follow-up: Testcontainers test executing the real V10 PL/pgSQL DO-block (H2 test only hand-mirrors it)
- [ ] Run the Testcontainers suites skipped in #109/#112/#113/#116; docker-build verify the #97/#98 Dockerfiles
- [ ] Full Playwright run using #111 mock-auth mode with backends up (last local attempt: 21 passed / 60 blocked on Auth0 egress)
- [ ] k6 load run against staging vs the 1000-concurrent-user target; validate order-service pool size (12) and autoscaling ceilings
- [ ] Chaos experiments staging signoff before flipping `dryRun=false`

### Resolved / notes
- Dual autoscaler for order/payment (HPA + KEDA fighting) — resolved in #107: KEDA is the single authority.
- Loyalty double-counting risk (unprotected `order.completed` consumer) — resolved in #119; >1 replica of order/promotion still gated on #112.

---

## Completed Tasks

### 2026-07-15 — Hardening continues (PRs #110–#120)
- [x] #110 — env-driven trace sampling, prod capped at 10%
- [x] #111 — e2e local test auth mode (`VITE_AUTH_MODE=mock`) — unblocks the auth-gated Playwright suite
- [x] #113 — product-service: batch-fetch images + cache paginated listings (browse hot-path N+1)
- [x] #114 — product-service: SCOPE_admin required on catalog PATCH endpoints (service-layer twin of #108)
- [x] #115 — CategoryController aligned with gateway post-rewrite path
- [x] #117 — CI dockerfile/build-pipeline alignment + reactor-pom drift guard
- [x] #118 — planning docs reconciled with the #95–#109 sprint
- [x] #119 — promotion-service: loyalty `order.completed` consumer deduped on outbox-event-id (duplicate delivery no longer double-counts lifetime spend)
- [x] #120 — media-service: ownership enforced on media read/download/list (IDOR)

### 2026-07-14 — Hardening Sprint (PRs #95–#109, merged to develop)

**Security & identity**
- [x] #95 — Gateway: stop committed-response header writes turning 401 into 500
- [x] #96 — Payment hardening: auth-gated checkout identity, `failureReason`, Stripe-laziness regression guards
- [x] #99 / #100 — Order-service: derive `userId` from JWT `sub` (never trust client input); closed remaining IDOR gaps on subscription, RMA, and order-by-number endpoints
- [x] #108 — Gateway: fixed catalog auth-policy drift across versioned and unversioned routes; category/product writes require `SCOPE_admin`

**API contracts**
- [x] #101 — Order-service: stable `PageResponse` envelope for `getUserOrders` (fixes `PageImpl` serialization drift)
- [x] #102 — Product & notification services: same `PageResponse` envelope for all paginated endpoints; product-catalog-mfe `PaginatedResponse` type aligned

**Build & deploy**
- [x] #97 / #98 — Reactor-standalone Dockerfiles for eureka-server, config-server, and api-gateway

**Performance & capacity**
- [x] #103 — Right-sized Hikari pools + shared-DB `max_connections` ceilings — budget in [docs/db-connection-budget.md](docs/db-connection-budget.md)
- [x] #104 — Gateway: response-timeout added; retries scoped to GET only
- [x] #105 — Prod profiles: verbose SQL/param logging silenced + CI drift guard
- [x] #106 — k6 load-test suite for the golden path (`performance-tests/k6/`)

**Resilience & autoscaling**
- [x] #107 — Autoscaling hygiene: HPAs for api-gateway/cart/user (cart & user capped at 2 per connection budget), KEDA the single autoscaler for order/payment (ceiling 8), JVM heap capped via `MaxRAMPercentage`
- [x] #109 — Cart-service: circuit breaker + bulkhead around the product-service client; fail-fast 503 with `Retry-After`, 4xx never trips the breaker

### 2026-04 — Post-plan features
- [x] Returns/RMA orchestration saga across order + notification services (`docs/day-37-rma-saga-summary.md`)
- [x] GraphQL BFF embedded in the api-gateway at `/graphql` (`docs/GRAPHQL_BFF.md`)
- [x] Recommendation service Phase 1, streaming co-occurrence, port 8092 (`docs/RECOMMENDATIONS.md`)
- [x] Review service (Mongo, port 8093)
- [x] Transactional outbox (polling) in order/payment services; real-time inventory SSE stream

### 10-week plan (Days 1–50, condensed)
- [x] **Days 1–30 (backend)** — infra (Postgres/MySQL/Mongo/Redis/Kafka/Elasticsearch), Eureka, Config Server, gateway + Auth0, gRPC, virtual threads, tracing, monitoring; all 11 original services; caching, rate limiting, resilience patterns, scheduled jobs, OpenAPI docs
- [x] **Days 31–45 (frontend)** — React 19 shell (Module Federation, Zustand, API client, MFE loader), Product Catalog / Cart / Checkout MFEs (React), User + Admin Dashboard MFEs (Angular), email notifications via MailHog (`docs/EMAIL_NOTIFICATIONS.md`)
- [x] **Days 46–50 (testing & production readiness)** — coverage ≥80% enforced (`docs/COVERAGE_REPORT.md`), Playwright E2E + axe a11y suites (`e2e/`), OWASP ZAP scan (`docs/SECURITY_SCAN.md`), `docker-compose.prod.yml`, `k8s/` Kustomize overlays, Helm umbrella chart, CI/CD workflows, Loki/Promtail logging, backup & DR (`docs/BACKUP_AND_DR.md`)
- [x] **Documentation** — aggregated Swagger at gateway, `docs/ONBOARDING.md`, `docs/OPERATIONS_RUNBOOK.md`, README documentation map
- [x] **2026-06** — CI green on develop, OWASP gate scaffold, structured JSON logging, Vault/ESO + cert-manager infra, Angular MFE federation bridge, frontend lint/audit blocking (PRs #79–#94)

---

## Service Ports

| Service | HTTP Port | gRPC Port |
|---------|-----------|-----------|
| API Gateway | 8080 | - |
| Eureka Server | 8761 | - |
| User Service | 8081 | - |
| Product Service | 8082 | 9091 |
| Cart Service | 8083 | - |
| Order Service | 8084 | - |
| Payment Service | 8085 | - |
| Inventory Service | 8086 | 9092 |
| Notification Service | 8087 | - |
| Search Service | 8088 | - |
| Media Service | 8089 | - |
| Promotion Service | 8090 | 9090 |
| Recommendation Service | 8092 | - |
| Review Service | 8093 | - |

### Key URLs
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **GraphQL BFF**: http://localhost:8080/graphql
- **Eureka Dashboard**: http://localhost:8761
- **Grafana**: http://localhost:3000 (admin/admin)
- **Zipkin**: http://localhost:9411

---

## Notes

- All services use Spring Boot 3.2.0 with Java 21
- Virtual threads enabled for improved concurrency
- Local development uses `SECURITY_ENABLED=false` to bypass Auth0
- Integration tests require all infrastructure containers running

---

## Documentation

The README has the full audience-organized [Documentation Map](README.md#documentation-map). Top picks:

| Document | Description |
|----------|-------------|
| [README.md](README.md) | Project overview + documentation map |
| [QUICKSTART.md](QUICKSTART.md) | Getting started guide |
| [docs/ONBOARDING.md](docs/ONBOARDING.md) | New-engineer onboarding flow |
| [docs/OPERATIONS_RUNBOOK.md](docs/OPERATIONS_RUNBOOK.md) | Production on-call playbook |
| [docs/BACKUP_AND_DR.md](docs/BACKUP_AND_DR.md) | Backup & DR runbooks |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | System architecture |
| [docs/SCALING_AND_IMPROVEMENTS.md](docs/SCALING_AND_IMPROVEMENTS.md) | Forward scaling roadmap |
| [docs/frontend-design-brief.md](docs/frontend-design-brief.md) | Frontend design system |
| [scripts/README.md](scripts/README.md) | Development scripts |
| [plan.md](plan.md) | Development plan + current status |
