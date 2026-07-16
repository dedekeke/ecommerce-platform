# Security Architecture

> Back to [README](../README.md).

This document describes the security architecture and measures implemented in the e-commerce platform's API Gateway.

## Overview

The API Gateway serves as the single entry point for all client requests and implements multiple layers of security:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                            API GATEWAY                                   │
├─────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│  │   Request   │  │  Security   │  │    Rate     │  │     IP      │    │
│  │   Logging   │──│   Headers   │──│   Limiting  │──│  Whitelist  │    │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘    │
│         │                │                │                │            │
│         └────────────────┴────────────────┴────────────────┘            │
│                                   │                                      │
│                    ┌──────────────▼──────────────┐                      │
│                    │    JWT Authentication       │                      │
│                    │    (Auth0 Integration)      │                      │
│                    └──────────────┬──────────────┘                      │
│                                   │                                      │
│                    ┌──────────────▼──────────────┐                      │
│                    │    Route Authorization      │                      │
│                    │    (Role-based Access)      │                      │
│                    └──────────────┬──────────────┘                      │
│                                   │                                      │
└───────────────────────────────────┼─────────────────────────────────────┘
                                    │
              ┌─────────────────────┼─────────────────────┐
              │                     │                     │
    ┌─────────▼─────────┐ ┌────────▼────────┐ ┌─────────▼─────────┐
    │   User Service    │ │ Product Service │ │   Order Service   │
    └───────────────────┘ └─────────────────┘ └───────────────────┘
```

## Security Layers

### 1. Rate Limiting

**Configuration File**: `RateLimiterConfig.java`

Redis-based rate limiting protects against abuse and DDoS attacks.

| Route | Replenish Rate | Burst Capacity | Purpose |
|-------|----------------|----------------|---------|
| `/api/products/**` | 200/sec | 400 | High-traffic catalog browsing |
| `/api/search/**` | 200/sec | 400 | Search queries |
| `/api/users/**` | 100/sec | 200 | User operations |
| `/api/cart/**` | 100/sec | 200 | Cart operations |
| `/api/orders/**` | 50/sec | 100 | Order creation (stricter) |
| `/api/payments/**` | 50/sec | 100 | Payment processing (stricter) |
| `/api/media/**` | 50/sec | 100 | File uploads (stricter) |

**Key Resolver Strategy**:
- Authenticated users: Rate limited by user ID
- Anonymous users: Rate limited by client IP
- Service-to-service: Rate limited by API key

**Rate Limit Response** (HTTP 429):
```json
{
  "timestamp": "2025-12-29T10:30:00Z",
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Please slow down your requests.",
  "path": "/api/products",
  "retryAfterSeconds": 1,
  "rateLimitInfo": {
    "remaining": "0",
    "limit": "200"
  }
}
```

### 2. Security Headers

**Configuration File**: `SecurityHeadersFilter.java`

All responses include OWASP-recommended security headers:

| Header | Value | Purpose |
|--------|-------|---------|
| `X-Frame-Options` | `DENY` | Prevents clickjacking attacks |
| `X-Content-Type-Options` | `nosniff` | Prevents MIME type sniffing |
| `X-XSS-Protection` | `1; mode=block` | Enables browser XSS filter |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains; preload` | Enforces HTTPS |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | Controls referrer information |
| `Permissions-Policy` | `geolocation=(), microphone=(), camera=(), payment=(self)` | Restricts browser features |
| `Content-Security-Policy` | See below | Controls resource loading |
| `Cache-Control` | `no-store, no-cache, must-revalidate, private` | Prevents caching of API responses |

**Content Security Policy**:
```
default-src 'self';
script-src 'self' 'unsafe-inline' 'unsafe-eval';
style-src 'self' 'unsafe-inline';
img-src 'self' data: https:;
font-src 'self' data:;
connect-src 'self' https:;
frame-ancestors 'none';
base-uri 'self';
form-action 'self'
```

### 3. Request Logging

**Configuration File**: `RequestLoggingFilter.java`

All requests are logged with security-sensitive data excluded.

**Logged Information**:
- Request method and path
- Client IP address (from X-Forwarded-For or direct connection)
- Authenticated user principal
- Response status code
- Request duration

**Excluded from Logs** (Sensitive Headers):
- `Authorization`
- `Cookie` / `Set-Cookie`
- `X-API-Key`
- `X-Auth-Token`
- `X-CSRF-Token`
- `Proxy-Authorization`

**Excluded from Logs** (Sensitive Query Parameters):
- `password`
- `token`
- `secret`
- `key`
- `credential`
- `auth`

**Sample Log Output**:
```
REQUEST [a1b2c3d4] GET /api/products params={category=electronics} client=192.168.1.100 user=user@example.com
RESPONSE [a1b2c3d4] status=200 duration=45ms path=/api/products
```

**Correlation ID**:
- Every request is assigned a unique correlation ID
- Passed to downstream services via `X-Correlation-ID` header
- Enables end-to-end request tracing

### 4. IP Whitelisting

**Configuration File**: `IpWhitelistFilter.java`

Admin endpoints are restricted to whitelisted IP addresses.

**Protected Paths**:
- `/api/admin/**`

**Default Whitelisted Addresses**:
- `127.0.0.1` (localhost IPv4)
- `::1` (localhost IPv6)
- `10.0.0.0/8` (Private Class A)
- `172.16.0.0/12` (Private Class B)
- `192.168.0.0/16` (Private Class C)

**Forbidden Response** (HTTP 403):
```json
{
  "timestamp": "2025-12-29T10:30:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Access denied. Your IP address is not authorized to access this resource.",
  "path": "/api/admin/users"
}
```

### 5. JWT Authentication (Auth0)

**Configuration File**: `SecurityConfig.java`

JWT tokens from Auth0 are validated for all protected endpoints.

**Token Validation**:
- Issuer validation (Auth0 domain)
- Audience validation (API identifier)
- Signature verification (RS256)
- Expiration check

**Public Endpoints** (No authentication required):
- `GET /api/products/**` - Product catalog browsing
- `GET /api/search/**` - Product search
- `GET /api/promotions/public/**` - Public promotions
- `/actuator/**` - Health checks
- `/swagger-ui/**`, `/v3/api-docs`, `/v3/api-docs/swagger-config` - Swagger UI assets and gateway-level config

**Protected Endpoints** (Require any valid JWT):
- `/aggregate/*/v3/api-docs/**` - Per-service OpenAPI schemas (require authentication to protect internal API contracts)

**Admin Endpoints** (Require `SCOPE_admin`):
- `POST/PUT/DELETE /api/products/**`
- `/api/admin/**`
- `PUT /api/orders/{id}/status` - Order status management

### 6. CORS Configuration

Cross-Origin Resource Sharing is configured for frontend applications:

**Allowed Origins**:
- `http://localhost:3000` (React Shell App)
- `http://localhost:5000` (React Shell - Vite)
- `http://localhost:5001` (Product Catalog MFE)
- `http://localhost:5002` (Cart MFE)
- `http://localhost:5003` (Checkout MFE)
- `http://localhost:4200` (User Dashboard - Angular)
- `http://localhost:4201` (Admin Dashboard - Angular)

**Allowed Methods**: GET, POST, PUT, DELETE, PATCH, OPTIONS

**Credentials**: Allowed

## Configuration Options

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SECURITY_ENABLED` | `true` | Enable/disable JWT authentication (secure-by-default; set `false` for local dev only) |
| `SECURITY_HSTS_ENABLED` | `true` | Enable HSTS header |
| `SECURITY_HSTS_MAX_AGE` | `31536000` | HSTS max-age in seconds |
| `SECURITY_CSP_ENABLED` | `true` | Enable Content-Security-Policy |
| `SECURITY_FRAME_OPTIONS` | `DENY` | X-Frame-Options value |
| `SECURITY_IP_WHITELIST_ENABLED` | `true` | Enable IP whitelisting |
| `SECURITY_IP_WHITELIST` | `127.0.0.1,::1,...` | Comma-separated whitelist |
| `GATEWAY_TRUSTED_PROXIES` | *(empty in dev; RFC1918 in k8s/helm)* | Comma-separated CIDRs whose `X-Forwarded-For` is trusted for client-IP resolution. **MUST be set to the real ingress/pod CIDR per environment** — see [Trusted proxy configuration (deploy checklist)](#trusted-proxy-configuration-deploy-checklist) |
| `LOGGING_REQUEST_ENABLED` | `true` | Enable request logging |
| `LOGGING_REQUEST_HEADERS` | `false` | Log request headers |
| `LOGGING_REQUEST_PARAMS` | `true` | Log query parameters |
| `REDIS_HOST` | `localhost` | Redis host for rate limiting |
| `REDIS_PORT` | `6379` | Redis port |
| `AUTH0_DOMAIN` | - | Auth0 domain |
| `AUTH0_AUDIENCE` | - | Auth0 API audience |

### Local Development

For local development without Auth0:

```yaml
security:
  enabled: false
  ip-whitelist:
    enabled: false
```

### Production

For production deployment:

```yaml
security:
  enabled: true
  headers:
    hsts:
      enabled: true
      max-age: 31536000
  ip-whitelist:
    enabled: true
    addresses: <production-admin-ips>
```

## Trusted proxy configuration (deploy checklist)

`GATEWAY_TRUSTED_PROXIES` is a **required per-environment deploy setting**, not an optional tuning knob. It is the list of reverse-proxy / load-balancer CIDRs whose `X-Forwarded-For` (XFF) header the gateway is allowed to believe when resolving the real client IP (`ClientIpResolver`).

### Why it matters

Two protections key off the resolved client IP:

1. **Guest-endpoint rate limiting** — the per-IP limits on the unauthenticated `POST /api/orders/guest` and `/api/cart/guest/**` routes (`#{@ipKeyResolver}`).
2. **`/api/admin/**` IP whitelist** (`IpWhitelistFilter`, `SECURITY_IP_WHITELIST`).

Behind an ingress/LB, the gateway's direct TCP peer is the ingress pod, and the real client IP arrives only in `X-Forwarded-For`. `ClientIpResolver` believes XFF **only when the immediate peer is in `GATEWAY_TRUSTED_PROXIES`**; otherwise it falls back to the socket peer address (fail-secure against XFF spoofing).

**If `GATEWAY_TRUSTED_PROXIES` is empty (or wrong) in a proxied deployment**, the ingress pod is not trusted, so XFF is ignored and *every* request resolves to the **same ingress pod IP**. Consequences:

- The guest rate limit becomes one shared bucket for all traffic — a single abuser exhausts it for everyone (effective DoS), or the shared counter never reflects real per-client volume (bypass).
- The admin IP whitelist compares the ingress pod IP (not the operator's IP) against the allowlist — either locking out all admins or, if the pod range is allowlisted, admitting everyone.

### Ingress note (verified)

The platform ingress is **ingress-nginx** (`ingressClassName: nginx`, `k8s/base/ingress/ingress.yaml`). ingress-nginx **sets `X-Forwarded-For` by default** (to the client connection IP; with `use-forwarded-headers` it appends to the inbound chain), so the real client IP is always available to the gateway once the ingress pod range is trusted. No extra ingress annotation is required to populate XFF.

### What to set, per environment

| Environment | Where | Value |
|-------------|-------|-------|
| docker-compose dev (`docker-compose.yml`) | env | **EMPTY** — gateway is exposed directly, no proxy; trusting nothing is correct and fail-secure. |
| docker-compose prod (`docker-compose.prod.yml`) | `.env` → `GATEWAY_TRUSTED_PROXIES` | The LB/proxy CIDR fronting the gateway (empty only if truly direct-exposed). |
| Kubernetes (kustomize) | `k8s/base/infra/api-gateway.yaml` ConfigMap | This cluster's ingress/pod-network CIDR (default ships broad RFC1918 — **narrow it**). |
| Helm | `values-prod.yaml` / `--set api-gateway.config.GATEWAY_TRUSTED_PROXIES=<cidr>` | This cluster's ingress/pod-network CIDR. |

The k8s/helm default is the RFC1918 set `10.0.0.0/8,172.16.0.0/12,192.168.0.0/16`. This is safe-by-topology (the gateway Service is `ClusterIP`, so the only possible direct peer is an in-cluster pod, i.e. an RFC1918 address) and covers every common CNI pod network. It is **not** a fail-open value — leaving it *empty* is strictly worse, because it re-creates the very bug this setting fixes (every client keyed on the shared ingress pod IP). It is, however, **deliberately broad**, so narrow it to the actual ingress/pod CIDR in each real cluster for tighter defense-in-depth.

> **Dependency — NetworkPolicy must be enforced by the CNI.** The "only an in-cluster pod can be the direct peer" argument relies on `k8s/base/policies/network-policy.yaml`: a `default-deny-all` policy plus `allow-ingress-to-gateway`, which permits ingress to the gateway pod (TCP 8080) **only from the `ingress-nginx` namespace**. Kubernetes `NetworkPolicy` objects are **inert unless the cluster CNI enforces them** — Calico, Cilium, and the managed cloud dataplanes (GKE Dataplane V2, AWS VPC CNI + policy add-on, Azure NPM) do; **plain Flannel does not**. On a non-enforcing CNI, *any* pod in the cluster can open a TCP connection straight to the gateway `ClusterIP` and present a spoofed RFC1918 `X-Forwarded-For`, defeating the broad default. In that case you **must** either (a) switch to a NetworkPolicy-enforcing CNI, or (b) narrow `GATEWAY_TRUSTED_PROXIES` to the exact ingress-nginx pod CIDR (the narrowest range containing the ingress pods) so a spoofing pod outside that range is not trusted. Verify enforcement with `kubectl get networkpolicy -n ecommerce` **and** a connectivity test from an unrelated pod.

### How to find the CIDR

```bash
# ingress-nginx controller pod IP(s) — the actual immediate peer
kubectl -n ingress-nginx get pods -o wide

# pod-network CIDR (the range ingress-nginx pods are allocated from)
kubectl cluster-info dump | grep -m1 -- --cluster-cidr
# or, on kubeadm:
kubectl -n kube-system get cm kubeadm-config -o yaml | grep -i podSubnet
```

Set `GATEWAY_TRUSTED_PROXIES` to the narrowest range that contains the ingress-nginx pods (the pod CIDR, or the specific ingress node/pod range).

### Verify after deploy

```bash
# 1. Env var is present on the gateway pod
kubectl -n <ns> exec deploy/api-gateway -- printenv GATEWAY_TRUSTED_PROXIES

# 2. Startup log shows the ranges were parsed
kubectl -n <ns> logs deploy/api-gateway | grep "ClientIpResolver initialised"
#   -> "ClientIpResolver initialised with N trusted proxy range(s)" (N > 0)

# 3. Distinct client IPs get distinct rate-limit buckets (not one shared ingress IP)
redis-cli --scan --pattern '*request_rate_limiter*'
```

## Filter Execution Order

Filters execute in the following order (lowest order first):

1. `SecurityHeadersFilter` (HIGHEST_PRECEDENCE)
2. `RateLimitExceededHandler` (HIGHEST_PRECEDENCE + 1)
3. `RequestLoggingFilter` (HIGHEST_PRECEDENCE + 2)
4. `IpWhitelistFilter` (HIGHEST_PRECEDENCE + 3)
5. Spring Security filters (JWT validation)
6. Route filters (Token Relay, Rate Limiting)

## Monitoring

### Rate Limiting Metrics

Monitor rate limiting via Redis:
- `redis-cli keys "*rate*"` - View rate limit keys
- Grafana Redis dashboard shows connected clients and commands/sec

### Request Logging

Request logs include:
- Request correlation ID for tracing
- Client IP for abuse detection
- Response duration for performance monitoring

### Security Alerts

Configure alerts for:
- High rate of 429 (Too Many Requests) responses
- High rate of 403 (Forbidden) responses
- Unusual request patterns from single IPs

## Security Hardening Changes (2026-04-29)

The following changes were applied on branch `feature/security-hardening`:

- **Secure-by-default**: `SECURITY_ENABLED` now defaults to `true` in the gateway, product-service, and cart-service docker profile. Previously a misconfigured deployment with a missing env var would run with auth disabled.
- **OpenAPI schema protection**: `/aggregate/*/v3/api-docs/**` routes now require authentication when security is enabled. Only the Swagger UI top-level config remains public.
- **Order status endpoint RBAC**: `PUT /api/orders/{id}/status` now requires `SCOPE_admin` via `@PreAuthorize`. `@EnableMethodSecurity` enabled on order-service `SecurityConfig`.
- **Jackson type validator**: `ObjectMapper.DefaultTyping.NON_FINAL` in product-service Redis cache config replaced with `BasicPolymorphicTypeValidator` allowlisting application packages, preventing gadget-chain attacks via crafted cache payloads.
- **window.__getAuthToken immutability**: Token bridge property defined with `Object.defineProperty(writable:false)` preventing MFE or extension hijacking.
- **Idempotent retry policy**: All MFE axios clients now restrict 5xx retries to idempotent HTTP methods (GET, HEAD, OPTIONS, PUT, DELETE). POST/PATCH mutations are not retried on 5xx to prevent duplicate orders.
- **Kafka replay guard**: Notification Kafka consumers now check the notification log for existing SENT/PENDING/RETRYING records before dispatching to prevent email spam via replay attacks.

## Security Best Practices

1. **Always use HTTPS in production** - HSTS header enforces this
2. **Rotate Auth0 secrets regularly** - Use environment variables
3. **Rotate the product-service MySQL password** - The credential `gahmaq-deqWit-4nixco` was previously committed to git history. The working tree is clean but the history is not. Rotate the credential and consider `git filter-repo` if the repository will be made public.
4. **Configure Kafka broker ACLs** - Restrict write access to `order.created`, `payment.completed`, `order.shipped` topics to the order-service and payment-service principals only.
5. **Review IP whitelist periodically** - Remove stale entries
6. **Monitor rate limit breaches** - Indicates potential attacks
7. **Keep dependencies updated** - Regular security patches
8. **Review logs for suspicious activity** - Automated alerting recommended

## Troubleshooting

### Rate Limit Too Restrictive

Increase limits in `application.yml`:
```yaml
spring.cloud.gateway.routes[n].filters[0].args:
  redis-rate-limiter.replenishRate: 200
  redis-rate-limiter.burstCapacity: 400
```

### IP Whitelist Blocking Legitimate Admin

Add IP to whitelist:
```yaml
security.ip-whitelist.addresses: 127.0.0.1,::1,<new-admin-ip>
```

### CORS Errors

Add origin to allowed list in `SecurityConfig.java`:
```java
configuration.setAllowedOrigins(Arrays.asList(
    "http://localhost:3000",
    "<new-frontend-origin>"
));
```

### JWT Validation Failing

Verify Auth0 configuration:
```bash
curl -I https://<auth0-domain>/.well-known/openid-configuration
```
