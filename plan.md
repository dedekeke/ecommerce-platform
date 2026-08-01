# E-Commerce Microservices Project: Development Plan

## Status 2026-07-15

The original 10-week feature plan below has been executed in full, plus a
post-plan feature wave (GraphQL BFF, Returns/RMA saga, transactional outbox,
recommendation and review services). Day-to-day tracking lives in
[todo.md](todo.md); the forward scaling roadmap is
[docs/SCALING_AND_IMPROVEMENTS.md](docs/SCALING_AND_IMPROVEMENTS.md). The
platform is feature-substantial:

- **Backend**: 12 business microservices (the planned 11 plus
  recommendation-service and review-service) behind Spring Cloud Gateway with
  an embedded GraphQL BFF; Eureka + Config Server; Kafka events with a
  transactional outbox; order + RMA sagas; gRPC on the order critical path.
- **Frontend**: React 19 shell with Module Federation hosting 5 MFEs
  (Product Catalog / Cart / Checkout in React, User + Admin Dashboards in
  Angular).
- **Ops**: docker-compose (dev/prod), k8s Kustomize overlays, Helm umbrella
  chart, CI/CD workflows, Loki/Promtail logging, Prometheus/Grafana/Zipkin,
  backup & DR plan.

**Focus has shifted from feature-build to scalability hardening and
production readiness.** The 2026-07 hardening waves shipped PRs #95–#120:
gateway 401/timeout/auth-policy fixes (#95, #104, #108), order-service JWT
identity + IDOR closure (#99/#100), payment and media hardening (#96, #120),
stable PageResponse envelopes (#101/#102), DB connection budget (#103), prod
log levels (#105), k6 load suite (#106), k8s autoscaling hygiene with KEDA
as single authority (#107), cart→product circuit breaker (#109), trace
sampling capped in prod (#110), e2e mock-auth mode (#111), product listing
cache (#113), catalog PATCH admin scoping (#114/#115), CI dockerfile drift
guard (#117), and loyalty consumer dedup (#119). In review: #112
(replica-safe scheduled jobs/outbox), #116 (archival schema fix), #121
(checkout raw-PAN removal, Stripe Elements only), #122 (REST checkout wired
to the real saga + Idempotency-Key).

**Remaining gaps to the "1000 concurrent users, production-ready" goal:**

1. **Multi-replica safety** — #112 (outbox SKIP LOCKED, ShedLock, dedicated
   scheduler pool, Flyway/validate) is the hard gate before running
   order/promotion with more than one replica.
2. **Verification is Docker/staging-gated** — Testcontainers migration-chain
   boot check for #112, real V10 DO-block test for #116, the skipped
   Testcontainers suites, a full Playwright run using #111 mock auth, a real
   k6 run against staging, and a ZAP re-run with security enabled.
3. **Hermetic builds** — the 10 service-tier Dockerfiles still cannot build
   standalone (apply the #97/#98 pattern, enforce via the #117 guard);
   review/recommendation are missing from the CD matrix.
4. **Security tail** — opaque-id enumeration normalization in order-service,
   internal-caller scopes after #114, NVD-keyed OWASP dependency-check gate.
5. **Prod build/infra caveats** — shell-app hardcodes federation remotes and
   disables minification in prod builds; single Zipkin + single-node
   Elasticsearch are SPOFs.

The actionable list lives in [todo.md](todo.md).

### Progress Summary

| Phase | Status | Description |
|-------|--------|-------------|
| Weeks 1-2 | **COMPLETE** | Infrastructure, Auth0, virtual threads, observability |
| Weeks 3-4 | **COMPLETE** | Core + transaction services (User, Product, Cart, Order, Payment, Inventory) |
| Week 5 | **COMPLETE** | Supporting services (Notification, Search, Media, Promotion) |
| Week 6 | **COMPLETE** | Caching, security, resilience, scheduled tasks, documentation |
| Weeks 7-8 | **COMPLETE** | Shell app + React MFEs (Product Catalog, Cart, Checkout) |
| Week 9 | **COMPLETE** | Angular MFEs (User Dashboard, Admin Dashboard) |
| Week 10 | **COMPLETE** | Testing (E2E/a11y/ZAP/coverage), K8s + Helm, CI/CD, logging, backup & DR |
| Post-plan | **COMPLETE** | GraphQL BFF, RMA saga, transactional outbox + inventory SSE, recommendation + review services |
| Current | **IN PROGRESS** | Scalability hardening + production readiness (PRs #95–#122+) |

---

## Project Overview and Architecture Decisions

Based on extensive research of the YAS (Yet Another Shop) reference architecture and modern best practices for 2025, this plan delivers a production-ready e-commerce platform combining React 19, Angular micro-frontends, Java 21 Spring Boot microservices, Auth0 authentication, gRPC for internal communication, and comprehensive observability.

**Technology Stack Summary:**
- **Frontend**: React 19 (Vite) + Angular (latest) with Module Federation
- **Backend**: Java 21 Spring Boot 3.2+ with virtual threads
- **Authentication**: Auth0 (direct SPA integration, no BFF for auth; GraphQL BFF for data aggregation)
- **Communication**: gRPC for internal high-frequency calls, REST for public APIs, GraphQL BFF at the gateway
- **Infrastructure**: Docker Compose + Kubernetes/Helm, Eureka, Spring Cloud Gateway, Kafka, PostgreSQL, MySQL, MongoDB
- **Observability**: Prometheus, Grafana, Zipkin, Loki

---

## Microservices Architecture

### Core Services (gateway + 12 business microservices)

1. **API Gateway** - Spring Cloud Gateway with Auth0 integration + GraphQL BFF
2. **User Service** - User management, profiles (PostgreSQL + REST)
3. **Product Service** - Product catalog management (MySQL + REST) - *Read-heavy workload*
4. **Cart Service** - Shopping cart operations (MongoDB + gRPC)
5. **Order Service** - Order processing, sagas, RMA (PostgreSQL + gRPC)
6. **Payment Service** - Payment processing (PostgreSQL + gRPC)
7. **Inventory Service** - Stock management (PostgreSQL + gRPC)
8. **Notification Service** - Email/SMS notifications (MongoDB + Kafka consumer)
9. **Search Service** - Elasticsearch-based search (Elasticsearch + REST)
10. **Media Service** - Image/file management (MongoDB + REST)
11. **Promotion Service** - Discounts, promotions, loyalty (MySQL + REST) - *Read-heavy workload*
12. **Recommendation Service** - Streaming co-occurrence recommendations (MongoDB + Kafka consumer)
13. **Review Service** - Product reviews (MongoDB + REST)

### Infrastructure Services

- **Eureka Server** - Service discovery
- **Config Server** - Centralized configuration
- **Kafka + Zookeeper** - Event streaming
- **PostgreSQL** - Transactional data (User, Order, Payment, Inventory services)
- **MySQL** - Catalog data (Product, Promotion services)
- **MongoDB** - Document storage (Cart, Notification, Media, Recommendation, Review services)
- **Elasticsearch** - Search engine
- **Zipkin** - Distributed tracing
- **Prometheus + Grafana + Loki** - Monitoring and logging

Compose files live at the repo root (`docker-compose.yml`,
`docker-compose.prod.yml`, `docker-compose.monitoring.yml`); Kubernetes
manifests under `k8s/`, Helm under `helm/ecommerce/`.

### Database Selection Rationale

**PostgreSQL (4 services):**
- **User Service**: Complex user profiles with relationships, strong ACID for auth data
- **Order Service**: Financial transactions requiring strong ACID compliance
- **Payment Service**: Financial data with absolute consistency requirements
- **Inventory Service**: Advanced locking mechanisms for stock reservations

**MySQL (2 services):**
- **Product Service**: Read-heavy catalog browsing, simple data model, benefits from fast reads
- **Promotion Service**: Read-heavy validation queries, simple data model, high cache hit rate

**MongoDB (5 services):**
- **Cart Service**: Flexible schema, TTL indexes for expiration, fast reads/writes
- **Notification Service**: Template storage, flexible notification logs
- **Media Service**: File metadata with flexible attributes
- **Recommendation Service**: Co-occurrence matrix + idempotency ledger
- **Review Service**: Flexible review documents

---

## Original 10-Week Plan (executed)

The full day-by-day breakdown that used to live here has been executed and
removed; per-day summaries survive in git history, [todo.md](todo.md), and
`docs/`. The shape of the plan, for reference:

**Week 1-2**: Infrastructure setup, project scaffolding, Auth0 configuration
**Week 3-4**: Core backend services (User, Product, Cart)
**Week 5**: Transaction services (Order, Payment, Inventory) with gRPC
**Week 6**: Supporting services and advanced features (caching, rate limiting, resilience, scheduled jobs)
**Week 7**: Frontend shell (Module Federation, Zustand, API client, MFE loader)
**Week 8**: React MFEs (Product Catalog, Cart, Checkout) and integration
**Week 9**: Angular MFEs (User Dashboard, Admin Dashboard)
**Week 10**: Testing, optimization, documentation, deployment (k8s, Helm, CI/CD, logging, DR)

Post-plan additions: GraphQL BFF embedded in the gateway
(`docs/GRAPHQL_BFF.md`), Returns/RMA orchestration saga
(`docs/day-37-rma-saga-summary.md`), transactional outbox in order/payment,
real-time inventory SSE stream, recommendation service
(`docs/RECOMMENDATIONS.md`), review service, email notifications via MailHog
(`docs/EMAIL_NOTIFICATIONS.md`).

---

## Key Architecture Decisions Summary

### When to Use gRPC vs REST

**Use gRPC for:**
- Order Service ↔ Cart Service (high-frequency cart retrieval)
- Order Service ↔ Inventory Service (stock reservation critical path)
- Order Service ↔ Payment Service (payment intent creation)
- Any internal service-to-service communication requiring low latency

**Use REST for:**
- API Gateway ↔ Frontend (browser compatibility)
- All public-facing APIs
- Product Service (standard CRUD, cacheable)
- User Service (infrequent calls)
- Admin operations

### When to Use Java 21 Virtual Threads

**Perfect for (I/O-bound operations):**
- Database queries in all services
- External API calls (payment gateways)
- Kafka message publishing
- Redis cache operations
- File I/O in Media Service

**Avoid for:**
- CPU-intensive operations (image processing - use dedicated thread pool)
- Code with `synchronized` blocks (replace with `ReentrantLock`)
- Short-lived operations (overhead not worth it)

### Micro-Frontend Strategy

**React 19 MFEs:**
- Product Catalog (most traffic, optimized with Vite)
- Shopping Cart (needs tight integration)
- Checkout Flow (complex state management)

**Angular MFEs:**
- User Dashboard (complex forms, Angular strength)
- Admin Dashboard (separate from customer experience)

**Shell App (React 19):**
- Hosts all MFEs
- Manages Auth0 authentication
- Provides global state (Zustand)
- Handles routing

### Security Model (as hardened, 2026-07)

- Gateway validates Auth0 JWTs; security matchers must cover both versioned
  and unversioned route peers (the rewrite filter runs after authorization —
  see PR #108).
- Services enforce their own authorization too: internal callers via
  load-balanced WebClient bypass the gateway, so gateway-only rules are not
  defense-in-depth (PRs #114, #120).
- User identity is always derived from the JWT `sub` claim, never from
  client-supplied fields (PRs #99/#100).
- Kafka consumers dedup on outbox-event-id — duplicate delivery is the
  outbox relay's expected failure mode (PR #119).
- Local development uses `SECURITY_ENABLED=false`; e2e uses mock auth
  (`VITE_AUTH_MODE=mock`, PR #111) — both hard-gated out of prod builds.

---

## Project Success Metrics

**Performance Targets:**
- API response time: p95 < 200ms
- gRPC call latency: p95 < 50ms
- Frontend initial load: < 3 seconds
- Lighthouse score: > 90

**Scalability Targets:**
- Support 1000+ concurrent users *(k6 golden-path suite merged in #106; full-scale run against staging still pending)*
- Handle 100+ orders per minute
- Process 10,000+ products

**Quality Targets:**
- Code coverage: > 80% (enforced; newer modules hold ≥90% on business classes)
- Zero critical security vulnerabilities *(pending: NVD-keyed dependency-check gate, ZAP re-run on staging with security enabled)*
- WCAG AA accessibility compliance
- 99.9% uptime

## Conclusion

The 10-week plan has been delivered: Java 21 virtual threads, gRPC on the
order critical path, React 19 + Angular micro-frontends, Auth0, and full
observability are in place. The project is now in a hardening phase —
multi-replica safety, hermetic builds, security tail work, and
staging-gated verification are what stand between the current state and the
production-readiness targets above. Track progress in [todo.md](todo.md) and
[docs/SCALING_AND_IMPROVEMENTS.md](docs/SCALING_AND_IMPROVEMENTS.md).
