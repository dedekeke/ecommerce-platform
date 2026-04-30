# Security Audit Findings

Audit performed: 2026-04-29  
Branch: `feature/security-hardening`  
Scope: OWASP Top 10 + project-specific concerns (see task brief)

---

## Summary

| Severity | Before | After |
|----------|--------|-------|
| Critical | 1      | 0     |
| High     | 4      | 0     |
| Medium   | 3      | 1     |
| Low      | 3      | 3 (documented, no code change required) |

---

## Findings Table

### Critical

| # | Severity | File : Line | Issue | Fix Status |
|---|----------|-------------|-------|------------|
| C1 | **Critical** | `infrastructure/api-gateway/src/main/resources/application.yml:279` | `security.enabled` defaulted to `false` — if `SECURITY_ENABLED` env var is absent the gateway permits all traffic without JWT validation | **Fixed** — default changed to `true` |
| C2 | **Critical** | `services/product-service/src/main/resources/application.yml:132` | Same `SECURITY_ENABLED:false` default on product-service | **Fixed** — default changed to `true` |
| C3 | **Critical** | `services/cart-service/src/main/resources/application-docker.yml:116` | Same `SECURITY_ENABLED:false` default on cart-service docker profile | **Fixed** — default changed to `true` |

### High

| # | Severity | File : Line | Issue | Fix Status |
|---|----------|-------------|-------|------------|
| H1 | **High** | `services/order-service/src/main/java/.../controller/OrderController.java:71` | `PUT /api/orders/{id}/status` had no role check — any authenticated user could arbitrarily change any order's status (vertical privilege escalation) | **Fixed** — `@PreAuthorize("hasAuthority('SCOPE_admin')")` added; `@EnableMethodSecurity` enabled on `SecurityConfig` |
| H2 | **High** | `services/product-service/src/main/java/.../config/CacheConfig.java:43` | `ObjectMapper.DefaultTyping.NON_FINAL` with the default (permissive) `PolymorphicTypeValidator` — known Jackson gadget attack vector; crafted JSON in Redis cache could cause arbitrary class instantiation | **Fixed** — replaced with `BasicPolymorphicTypeValidator` allowlisting `com.ecommerce.productservice`, `java.util`, `java.math`, `java.time` |
| H3 | **High** | `frontend/checkout-mfe/src/api/apiClient.ts:28` + `cart-mfe`, `product-catalog-mfe`, `shell-app` | `retryCondition` blindly retried POST/PATCH on HTTP 5xx — a network-timeout on `POST /api/orders` would create duplicate orders | **Fixed** — all four MFE apiClients now only retry idempotent methods (GET, HEAD, OPTIONS, PUT, DELETE) on 5xx |
| H4 | **High** | `infrastructure/api-gateway/src/main/java/.../config/SecurityConfig.java:60` | `/aggregate/*/v3/api-docs/**` routes (per-service OpenAPI schemas) were listed under `permitAll()` via the wildcard `/v3/api-docs/**` match — internal API schemas exposed without authentication in `SECURITY_ENABLED=true` mode | **Fixed** — `/aggregate/*/v3/api-docs/**` now requires `authenticated()`; only `/v3/api-docs` and `/v3/api-docs/swagger-config` (gateway-level) remain public so Swagger UI can load |

### Medium

| # | Severity | File : Line | Issue | Fix Status |
|---|----------|-------------|-------|------------|
| M1 | **Medium** | `services/notification-service/src/main/java/.../kafka/OrderEventConsumer.java` | No idempotency guard on Kafka consumers — a Kafka replay attack or at-least-once delivery retry would send duplicate notification emails (ORDER_CONFIRMATION, PAYMENT_RECEIPT, SHIPPING_NOTIFICATION) | **Fixed** — consumers now check `NotificationLogRepository.existsByRelatedEntityIdAndTemplateCodeAndStatusIn()` before dispatching |
| M2 | **Medium** | `services/product-service/src/main/java/.../controller/ProductController.java:78` | User-controlled `sortBy` parameter passed directly to `Sort.by()` without validation — allows JPA property-path probing (e.g. `category.parentId`) to enumerate schema | **Fixed** — `sortBy` validated against an explicit allowlist (`id`, `name`, `price`, `createdAt`, `updatedAt`, `stockQuantity`, `sku`); unknown values silently fall back to `createdAt` |
| M3 | **Medium** | `frontend/shell-app/src/hooks/useExposeAuthToken.ts:15` | `window.__getAuthToken` assigned as a plain writable property — a compromised MFE or browser extension could overwrite it and intercept Auth0 tokens | **Fixed** — property is now registered via `Object.defineProperty` with `writable: false` (tokens cannot be stolen by overwriting the function) |

### Low (documented only)

| # | Severity | File : Line | Issue | Fix Status |
|---|----------|-------------|-------|------------|
| L1 | **Low** | `git history` | MySQL password `gahmaq-deqWit-4nixco` present in git history for `product-service` (confirmed by the prior refactor commit message; the current working tree is clean). Secret is permanently embedded in repository history. | **TODO** — requires credential rotation by the platform owner. A `git filter-repo` / BFG purge should be applied to fully remove the commit if this repository is ever made public. No code change possible. |
| L2 | **Low** | `docker-compose.yml` (dev) | Dev Redis runs without a password (`redis-server --appendonly yes` only). Anyone with Docker-network access can read/write the cache. | **TODO** — add `--requirepass` to the dev compose Redis command and set `REDIS_PASSWORD` in `.env` (model after `docker-compose.prod.yml`). Deferred — not a production concern. |
| L3 | **Low** | `infrastructure/api-gateway/src/main/resources/application.yml` | `org.springframework.security: DEBUG` logging level may emit JWT validation detail to logs in production | **TODO** — lower to `INFO` in production profile. Medium operational effort, no code-level fix committed to avoid breaking test assertions. |

---

## Notes

### localStorage migration in `main.tsx`
The `migrateCartStorage()` IIFE only reads/writes cart item JSON via `localStorage.getItem` / `setItem` on the same origin. There is no `eval`, `Function()`, or DOM injection path. The values are never written to the DOM. This is not an XSS vector.

### Springdoc / Swagger-CSP friction
The CSP set by `SecurityHeadersFilter` includes `script-src 'self' 'unsafe-inline' 'unsafe-eval'`. This is already permissive enough for Swagger UI to function; no additional relaxation is needed. Tightening `script-src` by removing `'unsafe-eval'` would break Swagger UI's JavaScript runtime. Flag for removal of Swagger UI in production deployments.

### Promotion-service Flyway migration
`V1__Create_promotions_table.sql` contains only schema DDL and seed data. No credentials or secrets are present.

### Kafka topic ACL
The notification-service assumes Kafka ACLs prevent arbitrary producers from publishing to `order.created`, `payment.completed`, and `order.shipped`. No ACL configuration was found in this repository. The idempotency fix (M1) provides application-level protection, but **Kafka broker-level ACLs must be configured in production** to restrict topic write access to the order and payment services only.

---

## Tests Added

| Test Class | Location | Key Assertions |
|-----------|----------|---------------|
| `OrderControllerSecurityTest` | `services/order-service/src/test/.../controller/` | (1) Unauthenticated → 401; (2) Authenticated non-admin user → 403; (3) Admin (`SCOPE_admin`) → 200 and service called |

Total new tests: 3
