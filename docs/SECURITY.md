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
- `/swagger-ui/**` - API documentation

**Admin Endpoints** (Require `admin` scope):
- `POST/PUT/DELETE /api/products/**`
- `/api/admin/**`

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
| `SECURITY_ENABLED` | `false` | Enable/disable JWT authentication |
| `SECURITY_HSTS_ENABLED` | `true` | Enable HSTS header |
| `SECURITY_HSTS_MAX_AGE` | `31536000` | HSTS max-age in seconds |
| `SECURITY_CSP_ENABLED` | `true` | Enable Content-Security-Policy |
| `SECURITY_FRAME_OPTIONS` | `DENY` | X-Frame-Options value |
| `SECURITY_IP_WHITELIST_ENABLED` | `true` | Enable IP whitelisting |
| `SECURITY_IP_WHITELIST` | `127.0.0.1,::1,...` | Comma-separated whitelist |
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

## Security Best Practices

1. **Always use HTTPS in production** - HSTS header enforces this
2. **Rotate Auth0 secrets regularly** - Use environment variables
3. **Review IP whitelist periodically** - Remove stale entries
4. **Monitor rate limit breaches** - Indicates potential attacks
5. **Keep dependencies updated** - Regular security patches
6. **Review logs for suspicious activity** - Automated alerting recommended

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
