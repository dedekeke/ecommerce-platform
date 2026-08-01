# Local Validation Run — Summary

Date: 2026-06-14
Environment: macOS (darwin 25.5.0), Docker 28.5.1 / Compose v2.40.3, 36 GB RAM, 48 GB disk free.
Scope: Bring up stack locally, run Playwright E2E + OWASP ZAP baseline against the API gateway.
No application code was changed. No commits made.

## Stack brought up (docker compose + local overrides)

| Component | How | Status |
|-----------|-----|--------|
| postgres (5432→55432 remap) | compose, fresh volume (init created userdb/cartdb/orderdb/paymentdb/inventorydb) | UP (healthy) |
| redis | compose | UP (healthy) |
| zipkin | compose | UP (healthy) |
| eureka-server (8761) | local runtime Dockerfile (committed multi-stage Dockerfile is broken — see below) | UP (healthy) |
| api-gateway (8080) — ZAP target | compose, `Dockerfile.runtime` + autoconfig-exclude override | UP (healthy), SECURITY_ENABLED=true |
| Frontend: shell (5173), product-catalog (5001), cart (5002), checkout (5003) | `vite build` + `vite preview` | UP (HTTP 200) |

### NOT brought up (and why)
- Backend microservices (user/product/cart/order/payment/inventory/search/media/promotion):
  not required for the ZAP gateway scan or the frontend-only Playwright specs, and standing
  up all 11 Spring Boot services (each ~256–512 MB heap) exceeds the value of this run. The
  gateway routes to them return 503 (no Eureka registration) — expected.
- Angular MFEs user-dashboard (5004) / admin-dashboard (5005): not exercised by the E2E specs.
- kafka/zookeeper/mysql/mongodb/elasticsearch: only needed by the backend services above.

### Config-only workarounds applied (gitignored `docker-compose.override.yml`, NOT committed)
1. postgres host port 5432 → 55432 (5432 already held by `lore-db`). Internal compose comms
   (`postgres:5432`) unchanged.
2. Supplied `SPRING_DATASOURCE_USERNAME/PASSWORD` (intentionally removed from `.env`) for
   postgres-backed services — only relevant if those services are later started.
3. eureka-server built from the pre-built fresh JAR via a local runtime Dockerfile
   (`infrastructure/eureka-server/Dockerfile.runtime.local`, git-excluded). The committed
   multi-stage `infrastructure/eureka-server/Dockerfile` FAILS: its build context copies only
   eureka + common-library, but the root `pom.xml` lists sibling modules (media/promotion/
   recommendation/review-service) → Maven reactor error "Child module ... does not exist".
   *This is a real pre-existing Dockerfile/pom mismatch worth fixing separately.*
4. api-gateway: `SPRING_AUTOCONFIGURE_EXCLUDE` of the reactive OAuth2 *client* auto-config.
   Without this the gateway crashes at boot (see below).

## CRITICAL ENVIRONMENT BLOCKER: Auth0 egress is blocked (HTTP 403)

The configured Auth0 tenant `dev-vjhkmx73y28ucgcb.us.auth0.com` returns **403 Access Denied**
for both `/.well-known/openid-configuration` and `/authorize` from this machine (and from inside
the Docker network). This is an Auth0 attack-protection / IP block, not a code issue.

Two consequences:
1. **Gateway boot crash (resolved via config):** Spring Boot's `ReactiveOAuth2ClientAutoConfiguration`
   eagerly calls `ClientRegistrations.fromIssuerLocation()` at startup (driven by
   `spring.security.oauth2.client.registration.auth0` in `application-docker.yml`), which fails on
   the 403 and aborts the context — regardless of the app's `security.enabled` flag. Excluding the
   OAuth2 *client* auto-config (the gateway is a resource server and does not need it) let the
   gateway boot with `security.enabled=true`. JWT decoding is lazy so it does not block boot.
2. **Playwright suite mostly blocked (NOT resolvable without app changes):** the shell-app's
   `Auth0Provider` redirects to `https://dev-…/authorize` on load; that page is the Auth0
   "Access denied — request blocked" error, so the React SPA never mounts its own routes. Every
   spec that asserts on rendered app content fails. This needs a reachable Auth0 tenant (or test
   credentials / a mock) — the README explicitly states "Auth0 is in use".

## Playwright E2E result

- Command: `npx playwright test` (chromium + firefox + webkit), baseURL http://localhost:5173.
- Result: **21 passed / 60 failed** (81 tests = 27 specs × 3 browsers). Runtime 2.2m.
- Root cause of the 60 failures: the Auth0 403 egress block above — the app redirects to Auth0
  and never renders. Confirmed via the captured page snapshot showing the Auth0
  "Access denied" error page and `/authorize` → 403.
- HTML report: `security-reports/playwright-report/index.html`
- This is an infrastructure blocker, not a test/code defect. With a reachable Auth0 tenant the
  same build is expected to pass.

## OWASP ZAP baseline (gateway) result

- Command: `zap-baseline.py -t http://ecommerce-gateway:8080` on the compose network.
- Result: **PASS 65, WARN 2, FAIL 0.**
- Reports: `zap-baseline-report.html`, `.json`, `.md` in this folder.
- COVERAGE CAVEAT: the gateway root `/` returns HTTP 500 (no downstream services + Bearer-required),
  so ZAP's spider could not crawl beyond the base URL + a couple of probe paths. The scan therefore
  reflects the gateway's edge/header behaviour, not deep API surface. A fuller scan needs the
  backend services up so the gateway can route 2xx responses.

### Triaged alerts

| Alert | Risk | Real? | Notes / Fix |
|-------|------|-------|-------------|
| Application Error Disclosure [90022] (`/sitemap.xml` → 500) | Low | Mostly false-positive locally | The 500 is the gateway having no route for `/sitemap.xml` while requiring a Bearer token; body is empty (no stack trace leaked). Not a real disclosure here. In prod, add a catch-all 404/clean error response at the gateway so unmatched paths return 404, not 500. |
| Non-Storable Content [10049] (`/`, `/robots.txt`, `/sitemap.xml`) x3 | Informational | False-positive | ZAP flags that 500 responses with `Cache-Control: no-store` are non-cacheable. Expected for error responses; no action. |

### Notable PASSes (gateway security posture is good)
Security headers are present on every response: `X-Content-Type-Options: nosniff`,
`X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Cache-Control: no-store`,
`X-XSS-Protection: 0` (correct modern value). ZAP PASSed CSP, HSTS, X-Powered-By leak,
server-header leak, CORS misconfig, cookie flags, source-code disclosure, etc.
- HSTS (`Strict-Transport-Security`) is absent — acceptable over plain HTTP locally; it should
  be set at the TLS-terminating edge in staging/prod.
```
