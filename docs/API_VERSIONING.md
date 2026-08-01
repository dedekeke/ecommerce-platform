# API Versioning Policy

This platform exposes a public HTTP API through the Spring Cloud Gateway. As
of 2026-04-29 we run **two parallel route prefixes** for every backend service
so existing clients keep working while new clients integrate against the
versioned surface.

[← Back to README](../README.md)

## TL;DR

| Path                       | Status     | When to use                          |
|----------------------------|------------|--------------------------------------|
| `/api/<resource>/**`       | Deprecated | Legacy clients only                  |
| `/api/v1/<resource>/**`    | Active     | All new integrations                 |
| `/api/v2/<resource>/**`    | Future     | Reserved for the next breaking bump  |

Both unversioned and `/api/v1` routes hit the same backend endpoints today.
The unversioned routes will be removed on **30 April 2027** (advertised via
the `Sunset` response header).

## Versioning scheme

We follow **URL versioning** (`/api/v1/...`, `/api/v2/...`). Major version
bumps mean a backwards-incompatible change to the API surface:

* Field removal or rename in a response body
* Required-field addition to a request body
* Status-code semantics changes
* Resource URL restructuring
* Authentication contract changes

Additive, non-breaking changes (new endpoints, new optional response fields,
new optional query parameters) **do not** require a version bump — they are
shipped under the existing version.

## Deprecation contract

Unversioned routes carry two response headers on every response:

```
Deprecation: true
Sunset: Fri, 30 Apr 2027 23:59:59 GMT
Link: </api/v1/<resource>>; rel="successor-version"
```

These are RFC-aligned (`Deprecation` per draft-ietf-httpapi-deprecation-header,
`Sunset` per RFC 8594, `Link rel=successor-version` per RFC 5829). Clients
that surface deprecation warnings to operators (e.g. our internal Java SDK,
the `curl --include` smoke tests in CI) will pick these up automatically.

The 6-month minimum deprecation window starts from the **public announcement
date**. The current sunset is centrally controlled via the
`API_DEPRECATION_SUNSET` env var so we can roll the date forward in one place
if a major customer needs more lead time.

## Implementation

The gateway lives at `infrastructure/api-gateway`. Routes are defined in two
places (intentional duplication so neither YAML nor code drifts unnoticed):

1. **Declarative** — `src/main/resources/application.yml` under
   `spring.cloud.gateway.routes`. Each backend service has both an unversioned
   route and a `*-v1` route. The v1 route uses `RewritePath` to strip `/v1`
   so backend services never see the version prefix:

   ```yaml
   - id: products-v1
     uri: lb://product-service
     predicates:
       - Path=/api/v1/products/**
     filters:
       - RewritePath=/api/v1/(?<segment>.*), /api/${segment}
       - AddRequestHeader=X-API-Version, v1
   ```

2. **Programmatic** — `config/GatewayRoutesConfig.java` mirrors the same
   routes via the `RouteLocatorBuilder` DSL with the same path/uri pairs.
   The unversioned routes here add the `Deprecation`/`Sunset` headers; the
   versioned routes do not.

Routes covered (12 services × 2 routes + admin routes):

* user, product, cart, order, payment, inventory, notification
* search, media, promotion, recommendation, review

The `GatewayRoutesConfigTest` enforces that every backend has both routes
present in the route table.

## Migration plan: v1 → v2

When (not if) we need to ship a breaking change:

1. **Pick a single endpoint or coherent set** to break. Don't bump the entire
   API surface for one breaking field — most v2 routes will simply forward to
   the same backend, identical to v1.
2. **Add a `*-v2` gateway route** in both `application.yml` and
   `GatewayRoutesConfig.java`. Use `RewritePath=/api/v2/(?<seg>.*), /api/v2/${seg}`
   so the backend sees the version (if it needs to branch behaviour) or
   continue to strip if v2 just changes shape via DTO substitution.
3. **Backend implements v2 alongside v1** — a separate controller class is
   the cleanest split (`UserControllerV1`, `UserControllerV2`). Sharing a
   service layer is fine; sharing controllers gets messy.
4. **Add `Link: </api/v2/<resource>>; rel="successor-version"`** on the v1
   route, plus `Deprecation: true` and a fresh `Sunset` 6+ months out.
5. **Document the diff** in this file under a new "v1 → v2 changelog" section
   so client teams know exactly what changed.
6. **Announce** via the changelog channel and the API docs (Swagger UI at
   `/swagger-ui.html`).
7. **Remove v1** only after Sunset passes AND access logs show < 0.1% v1
   traffic for two consecutive weeks.

## Client guidance

* **New code** — always integrate against `/api/v1/...`. Set an `Accept`
  header version-pin if you want extra defence against accidental migrations.
* **Existing code on `/api/...`** — schedule the migration during your next
  quarterly maintenance window. The unversioned routes will keep working
  until the advertised Sunset date.
* **Watch for the `Deprecation: true` header** — if your HTTP client surfaces
  this (axios interceptor, OkHttp interceptor, etc.), log it as a warning.
* **Read the `Link: ...; rel="successor-version"` header** to discover the
  recommended replacement URL programmatically.

## Testing

* `GatewayRoutesConfigTest` asserts the route table contains both unversioned
  and v1 routes for every service. Adding a new backend service requires
  extending this test; CI fails if you forget.
* Manual smoke check via `curl`:

  ```bash
  curl -s -i http://localhost:8080/api/products | head -5
  # Should include: Deprecation: true, Sunset: ...

  curl -s -i http://localhost:8080/api/v1/products | head -5
  # Should NOT include those headers.
  ```

## See also

* `docs/API_DOCUMENTATION.md` — endpoint-level reference
* `docs/ARCHITECTURE.md` — gateway placement in the overall system
* `infrastructure/api-gateway/src/main/resources/application.yml` — route source of truth
