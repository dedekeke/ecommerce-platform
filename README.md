# E-Commerce Platform - Microservices Architecture

A production-ready e-commerce platform built with modern microservices architecture, combining Java 21 Spring Boot backend services with React 19 and Angular micro-frontends.

## Architecture Overview

```
                              ┌──────────────────┐
                              │   API Gateway    │
                              │    (Port 8080)   │
                              └────────┬─────────┘
                                       │
         ┌─────────────────────────────┼─────────────────────────────┐
         │                             │                             │
         ▼                             ▼                             ▼
   ┌───────────┐               ┌─────────────┐               ┌───────────┐
   │   User    │               │   Product   │               │   Cart    │
   │  Service  │               │   Service   │               │  Service  │
   │  (8081)   │               │   (8082)    │               │  (8083)   │
   └───────────┘               └─────────────┘               └─────┬─────┘
         │                             │                           │
         │                             │                           ▼
         │                             │                    ┌───────────┐
         │                             │                    │   Order   │
         │                             │                    │  Service  │
         │                             │                    │  (8084)   │
         │                             │                    └─────┬─────┘
         │                             │                          │
         │                             │        ┌─────────────────┼─────────────────┐
         │                             │        │                 │                 │
         │                             │        ▼                 ▼                 ▼
         │                             │  ┌───────────┐    ┌───────────┐    ┌─────────────┐
         │                             │  │ Inventory │    │  Payment  │    │Notification │
         │                             │  │  Service  │    │  Service  │    │   Service   │
         │                             │  │  (8086)   │    │  (8085)   │    │   (8087)    │
         │                             │  └───────────┘    └───────────┘    └─────────────┘
         │                             │
         │                             ▼
         │                    ┌─────────────────────────────────────────┐
         │                    │           Supporting Services           │
         │                    │  ┌─────────┐ ┌─────────┐ ┌───────────┐  │
         │                    │  │ Search  │ │  Media  │ │ Promotion │  │
         │                    │  │ (8088)  │ │ (8089)  │ │  (8090)   │  │
         │                    │  └─────────┘ └─────────┘ └───────────┘  │
         │                    └─────────────────────────────────────────┘
         │
         ▼
   ┌─────────────────────────────────────────────────────────────────────┐
   │                        Infrastructure                               │
   │  ┌─────────┐ ┌───────────┐ ┌───────┐ ┌───────┐ ┌─────┐ ┌────────┐   │
   │  │ Eureka  │ │PostgreSQL │ │MongoDB│ │ Redis │ │Kafka│ │Elastic │   │
   │  │ (8761)  │ │  (5432)   │ │(27017)│ │(6379) │ │(9092│ │ (9200) │   │
   │  └─────────┘ └───────────┘ └───────┘ └───────┘ └─────┘ └────────┘   │
   └─────────────────────────────────────────────────────────────────────┘
```

### Features

**Infrastructure**
- Eureka Server for service discovery
- API Gateway with Spring Cloud Gateway
- Auth0 integration for authentication
- gRPC setup for internal communication
- Kafka for event-driven architecture
- Distributed tracing with Zipkin
- Prometheus & Grafana for monitoring

**Core Services**
- User Service (PostgreSQL + REST)
- Product Service (PostgreSQL + REST + Redis caching)
- Cart Service (MongoDB + gRPC)
- Order Service (PostgreSQL + gRPC + Saga pattern)
- Payment Service (PostgreSQL + gRPC)
- Inventory Service (PostgreSQL + gRPC)

**Supporting Services**
- Notification Service (MongoDB + Kafka consumer)
- Search Service (Elasticsearch + Kafka sync)
- Media Service (MongoDB + file storage)
- Promotion Service (PostgreSQL + Redis caching)

**Advanced Features**
- Redis caching layer with cache warming
- Rate limiting and security headers
- Circuit breakers with Resilience4j
- Scheduled tasks for cleanup and reports
- Comprehensive API documentation

## Technology Stack

| Category | Technologies |
|----------|-------------|
| **Backend** | Java 21, Spring Boot 3.2+, Spring Cloud |
| **Communication** | gRPC (internal), REST (public APIs) |
| **Databases** | PostgreSQL, MongoDB, Elasticsearch |
| **Caching** | Redis |
| **Messaging** | Apache Kafka |
| **Authentication** | Auth0 (OAuth2/JWT) |
| **Observability** | Prometheus, Grafana, Zipkin |
| **Resilience** | Resilience4j (Circuit Breaker, Retry, Bulkhead) |
| **Documentation** | OpenAPI/Swagger |

## Quick Start

See [QUICKSTART.md](QUICKSTART.md) for detailed setup instructions.

### Prerequisites
- Java 21 (JDK 21+)
- Maven 3.8+
- Docker Desktop
- Node.js 18+ (for frontend)

### Start Infrastructure
```bash
# Copy environment file
cp .env.template .env

# Start infrastructure services
docker-compose up -d

# Build all services
mvn clean install -DskipTests

# Run all services
./scripts/run-all-services.sh
```

### Access Points

| Service | URL | Description |
|---------|-----|-------------|
| API Gateway | http://localhost:8080 | Main entry point |
| Swagger UI | http://localhost:8080/swagger-ui.html | API documentation |
| Eureka Dashboard | http://localhost:8761 | Service registry |
| Grafana | http://localhost:3000 | Monitoring dashboards |
| Zipkin | http://localhost:9411 | Distributed tracing |

## Project Structure

```
ecommerce-platform/
├── common-library/          # Shared utilities, DTOs, proto definitions
├── infrastructure/
│   ├── eureka-server/       # Service discovery
│   ├── config-server/       # Centralized configuration
│   └── api-gateway/         # API Gateway with Auth0
├── services/
│   ├── user-service/        # User management (PostgreSQL)
│   ├── product-service/     # Product catalog (PostgreSQL + Redis)
│   ├── cart-service/        # Shopping cart (MongoDB + gRPC)
│   ├── order-service/       # Order processing (PostgreSQL + gRPC)
│   ├── payment-service/     # Payment processing (PostgreSQL + gRPC)
│   ├── inventory-service/   # Stock management (PostgreSQL + gRPC)
│   ├── notification-service/# Email/SMS (MongoDB + Kafka)
│   ├── search-service/      # Product search (Elasticsearch)
│   ├── media-service/       # File storage (MongoDB)
│   └── promotion-service/   # Discounts (PostgreSQL + Redis)
├── scripts/                 # Development and deployment scripts
├── docker/                  # Docker configurations
└── docs/                    # Documentation
```

## Key Patterns & Features

### Virtual Threads (Java 21)
All services use virtual threads for improved concurrency on I/O-bound operations. See [docs/VIRTUAL_THREADS.md](docs/VIRTUAL_THREADS.md) for details.

### Resilience Patterns
- **Circuit Breaker**: Prevents cascade failures
- **Retry with Backoff**: Handles transient failures
- **Bulkhead**: Limits concurrent calls
- **Time Limiter**: Enforces timeouts

See [docs/RESILIENCE_PATTERNS.md](docs/RESILIENCE_PATTERNS.md) for configuration details.

### Caching Strategy
- Product catalog: 1-hour TTL
- Active promotions: 30-minute TTL
- User profiles: 15-minute TTL
- Cache warming on startup

See [docs/CACHING_STRATEGY.md](docs/CACHING_STRATEGY.md) for implementation details.

### Scheduled Tasks
- Cart cleanup: Daily at 2 AM
- Expired reservations: Every 15 minutes
- Abandoned orders: Daily at 4 AM
- Sales reports: Daily at 1 AM

See [docs/SCHEDULED_TASKS.md](docs/SCHEDULED_TASKS.md) for the complete list.

## Documentation Map

All docs live in [`docs/`](docs/) with a back-link to this README. Organized by audience.

### Getting Started
| Doc | Description |
|-----|-------------|
| [QUICKSTART.md](QUICKSTART.md) | Five-minute clone-to-running setup |
| [docs/ONBOARDING.md](docs/ONBOARDING.md) | 1–2 hour onboarding flow for new engineers |
| [docs/LOCAL_DEV_PROFILE.md](docs/LOCAL_DEV_PROFILE.md) | Per-service `personal` profile setup |
| [docs/IDEA_RUN_CONFIGURATIONS.md](docs/IDEA_RUN_CONFIGURATIONS.md) | IntelliJ IDEA run configurations |
| [docs/AUTH0_SETUP.md](docs/AUTH0_SETUP.md) | Configure Auth0 tenant and apps |

### Architecture & Patterns
| Doc | Description |
|-----|-------------|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | System architecture overview (C4) |
| [docs/INTEGRATION_PATTERNS.md](docs/INTEGRATION_PATTERNS.md) | Service-to-service patterns and contracts |
| [docs/VIRTUAL_THREADS.md](docs/VIRTUAL_THREADS.md) | Java 21 virtual threads usage |
| [docs/RESILIENCE_PATTERNS.md](docs/RESILIENCE_PATTERNS.md) | Resilience4j circuit breakers, retry, bulkhead |
| [docs/CACHING_STRATEGY.md](docs/CACHING_STRATEGY.md) | Redis caching layer and TTLs |
| [docs/SCHEDULED_TASKS.md](docs/SCHEDULED_TASKS.md) | Cron jobs and batch processing |
| [docs/TRACING_SETUP.md](docs/TRACING_SETUP.md) | Distributed tracing with Zipkin |

### API & Frontend
| Doc | Description |
|-----|-------------|
| [docs/API_DOCUMENTATION.md](docs/API_DOCUMENTATION.md) | REST API reference (also at `/swagger-ui.html`) |
| [docs/EMAIL_NOTIFICATIONS.md](docs/EMAIL_NOTIFICATIONS.md) | Notification flow and MailHog wiring |
| [docs/MFE_INTEGRATION_TEST.md](docs/MFE_INTEGRATION_TEST.md) | Manual smoke test playbook for MFEs |
| [docs/COVERAGE_REPORT.md](docs/COVERAGE_REPORT.md) | Frontend test coverage snapshot |
| [docs/frontend-design-brief.md](docs/frontend-design-brief.md) | Design system, tokens, UI/UX guidelines |

### Operations & Production
| Doc | Description |
|-----|-------------|
| [docs/OPERATIONS_RUNBOOK.md](docs/OPERATIONS_RUNBOOK.md) | On-call playbook: alerts, rollback, restart, DR |
| [docs/BACKUP_AND_DR.md](docs/BACKUP_AND_DR.md) | RTO/RPO targets, restore runbooks per datastore |
| [docs/PERFORMANCE_TESTING.md](docs/PERFORMANCE_TESTING.md) | Load and performance test methodology |
| [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) | Common issues and fixes |

### Security
| Doc | Description |
|-----|-------------|
| [docs/SECURITY.md](docs/SECURITY.md) | Security architecture (auth, headers, rate limits) |
| [docs/SECURITY_SCAN.md](docs/SECURITY_SCAN.md) | OWASP ZAP scan procedure |

### Other
| Doc | Description |
|-----|-------------|
| [scripts/README.md](scripts/README.md) | Development scripts reference |
| [k8s/README.md](k8s/README.md) | Kubernetes manifests and Kustomize overlays |
| [monitoring/README.md](monitoring/README.md) | Loki/Promtail/Grafana stack |
| [todo.md](todo.md) | Progress tracker |
| [plan.md](plan.md) | Long-term development plan |

## Service Ports

| Service | HTTP Port | gRPC Port |
|---------|-----------|-----------|
| API Gateway | 8080 | - |
| Eureka Server | 8761 | - |
| Config Server | 8888 | - |
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

## Development

### Running Tests
```bash
# Unit tests
mvn test

# Integration tests
mvn verify

# Coverage report
mvn jacoco:report
```

### Building Docker Images
```bash
./scripts/build-all.sh --deploy
```

### Checking Service Health
```bash
./scripts/check-services.sh
```

## Deployment

Production-readiness artifacts live alongside the source for the same workflow used in dev.

### Production Docker Compose
```bash
cp production.env.example production.env   # then fill in real values
docker compose -f docker-compose.prod.yml --env-file production.env up -d
```
- Resource limits, restart policies, json-file log rotation, no debug ports.
- Image tags pin immutable SemVer (`ecommerce/<svc>:1.0.0`); see file header for tagging strategy.

### Kubernetes (Kustomize)
```bash
kubectl apply -k k8s/overlays/staging
kubectl apply -k k8s/overlays/production
```
- Manifests for 11 backends + 6 frontends.
- HPA on `product-service`, `order-service`, `payment-service`.
- Default-deny `NetworkPolicy` plus explicit allow rules.
- See [k8s/README.md](k8s/README.md) for secret-management (sealed-secrets recommended).

### Helm umbrella chart
```bash
helm upgrade --install ecommerce ./helm/ecommerce \
  -f helm/ecommerce/values-prod.yaml \
  -n ecommerce-prod --create-namespace
```
- 11 backend subcharts in `helm/ecommerce/charts/`.
- `values.yaml`, `values-staging.yaml`, `values-prod.yaml`.

### CI/CD
- `.github/workflows/ci.yml` — PR build + test + lint + dependency scan.
- `.github/workflows/cd-staging.yml` — push to `develop` → build/push images → helm-upgrade staging.
- `.github/workflows/cd-production.yml` — `v*.*.*` tag → build/push → helm-upgrade prod (manual approval).

### Centralized logging
Loki + Promtail + Grafana — see [monitoring/README.md](monitoring/README.md) for install instructions and the bundled `services-logs` dashboard.

### Backup & DR
[docs/BACKUP_AND_DR.md](docs/BACKUP_AND_DR.md) — RTO 4h / RPO 1h, restore runbooks for every datastore, DR table-top template.

## Upcoming
- Frontend Shell and React Micro-Frontends
- Angular Micro-Frontends
- Testing, Optimization, Documentation

See [plan.md](plan.md) for the complete development plan.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

**Built with Java 21, Spring Boot, gRPC, and Virtual Threads**
