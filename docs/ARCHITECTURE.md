# System Architecture

> Back to [README](../README.md).

## Overview

The E-Commerce Platform is built on a microservices architecture using Java 21 and Spring Boot 3.2+. This document describes the high-level architecture, communication patterns, and data flow.

## Architecture Diagram

```
                                    ┌─────────────────────┐
                                    │     Clients         │
                                    │  (Web/Mobile/API)   │
                                    └──────────┬──────────┘
                                               │
                                    ┌──────────▼──────────┐
                                    │    API Gateway      │
                                    │    (Port 8080)      │
                                    │  ┌────────────────┐ │
                                    │  │ Rate Limiting  │ │
                                    │  │ JWT Validation │ │
                                    │  │ Security Headers│ │
                                    │  └────────────────┘ │
                                    └──────────┬──────────┘
                                               │
          ┌────────────────────────────────────┼────────────────────────────────────┐
          │                                    │                                    │
          ▼                                    ▼                                    ▼
┌─────────────────┐                 ┌─────────────────┐                 ┌─────────────────┐
│   User Service  │                 │ Product Service │                 │  Cart Service   │
│   (Port 8081)   │                 │   (Port 8082)   │                 │   (Port 8083)   │
│   PostgreSQL    │                 │   PostgreSQL    │                 │    MongoDB      │
│      REST       │                 │   REST + Cache  │                 │      gRPC       │
└─────────────────┘                 └─────────────────┘                 └────────┬────────┘
                                                                                  │
                                                                       ┌──────────▼──────────┐
                                                                       │   Order Service     │
                                                                       │    (Port 8084)      │
                                                                       │    PostgreSQL       │
                                                                       │  gRPC + REST + Saga │
                                                                       └──────────┬──────────┘
                                                                                  │
                           ┌──────────────────────────────────────────────────────┼───────────┐
                           │                              │                       │           │
                           ▼                              ▼                       ▼           ▼
               ┌─────────────────┐            ┌─────────────────┐    ┌─────────────────┐    │
               │ Inventory Svc   │            │ Payment Service │    │Notification Svc │    │
               │  (Port 8086)    │            │   (Port 8085)   │    │  (Port 8087)    │    │
               │  PostgreSQL     │            │   PostgreSQL    │    │   MongoDB       │    │
               │     gRPC        │            │  gRPC + Gateway │    │     Kafka       │    │
               └─────────────────┘            └─────────────────┘    └─────────────────┘    │
                                                                                            │
          ┌─────────────────────────────────────────────────────────────────────────────────┘
          │
          ▼
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│                               Supporting Services                                         │
│  ┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐                │
│  │  Search Service │       │  Media Service  │       │Promotion Service│                │
│  │   (Port 8088)   │       │   (Port 8089)   │       │   (Port 8090)   │                │
│  │  Elasticsearch  │       │    MongoDB      │       │   PostgreSQL    │                │
│  │      REST       │       │      REST       │       │  REST + Cache   │                │
│  └─────────────────┘       └─────────────────┘       └─────────────────┘                │
└──────────────────────────────────────────────────────────────────────────────────────────┘
          │
          ▼
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│                               Infrastructure Layer                                        │
│                                                                                          │
│  ┌─────────┐  ┌───────────┐  ┌───────┐  ┌───────┐  ┌───────┐  ┌────────────┐           │
│  │ Eureka  │  │PostgreSQL │  │MongoDB│  │ Redis │  │ Kafka │  │Elasticsearch│           │
│  │ (8761)  │  │  (5432)   │  │(27017)│  │(6379) │  │(9092) │  │  (9200)    │           │
│  └─────────┘  └───────────┘  └───────┘  └───────┘  └───────┘  └────────────┘           │
│                                                                                          │
│  ┌─────────┐  ┌───────────┐  ┌─────────────┐                                            │
│  │ Zipkin  │  │Prometheus │  │   Grafana   │                                            │
│  │ (9411)  │  │  (9090)   │  │   (3000)    │                                            │
│  └─────────┘  └───────────┘  └─────────────┘                                            │
└──────────────────────────────────────────────────────────────────────────────────────────┘
```

## Service Responsibilities

### Core Services

| Service | Purpose | Database | Communication |
|---------|---------|----------|---------------|
| **User Service** | User profiles, addresses, preferences | PostgreSQL | REST |
| **Product Service** | Product catalog, categories, inventory display | PostgreSQL | REST + Redis Cache |
| **Cart Service** | Shopping cart management, session handling | MongoDB | gRPC |
| **Order Service** | Order creation, status management, saga orchestration | PostgreSQL | gRPC + REST |
| **Payment Service** | Payment processing, refunds, payment gateway integration | PostgreSQL | gRPC |
| **Inventory Service** | Stock management, reservations, reorder alerts | PostgreSQL | gRPC |

### Supporting Services

| Service | Purpose | Database | Communication |
|---------|---------|----------|---------------|
| **Notification Service** | Email/SMS notifications, templates | MongoDB | Kafka Consumer |
| **Search Service** | Full-text product search, faceted search | Elasticsearch | REST + Kafka |
| **Media Service** | Image upload, thumbnails, file storage | MongoDB | REST |
| **Promotion Service** | Discount codes, promotions, validation | PostgreSQL | REST + Redis Cache |

## Communication Patterns

### gRPC (Internal High-Performance)

Used for:
- Order Service -> Cart Service (get cart items)
- Order Service -> Inventory Service (reserve stock)
- Order Service -> Payment Service (process payment)

Benefits:
- Binary protocol (smaller payloads)
- Strong typing via Protocol Buffers
- Streaming support
- ~10x faster than REST

### REST (Public APIs)

Used for:
- All client-facing endpoints
- Product catalog browsing
- User management
- Search queries

Benefits:
- Universal compatibility
- Cacheable responses
- Human-readable
- OpenAPI/Swagger documentation

### Kafka (Event-Driven)

Events published:
- `order.created` - New order placed
- `order.confirmed` - Order confirmed
- `payment.completed` - Payment successful
- `inventory.updated` - Stock changed
- `product.created/updated` - Product changes

Consumers:
- Notification Service (sends emails)
- Search Service (reindexes products)

## Data Flow: Order Creation

```
1. Client -> API Gateway (JWT validation)
       ↓
2. API Gateway -> Order Service (create order request)
       ↓
3. Order Service -> Cart Service [gRPC] (get cart items)
       ↓
4. Order Service -> Inventory Service [gRPC] (reserve stock)
       ↓
5. Order Service -> Promotion Service [REST] (validate discount)
       ↓
6. Order Service -> Payment Service [gRPC] (create payment intent)
       ↓
7. Order Service -> Database (save order)
       ↓
8. Order Service -> Kafka (publish OrderCreatedEvent)
       ↓
9. Notification Service <- Kafka (send confirmation email)
```

## Database Selection Rationale

### PostgreSQL (4 services)
- **User Service**: Complex user profiles, relationships, ACID compliance
- **Order Service**: Financial transactions, strong consistency
- **Payment Service**: Financial data, audit requirements
- **Inventory Service**: Advanced locking for stock reservations

### MongoDB (3 services)
- **Cart Service**: Flexible schema, TTL indexes, fast reads/writes
- **Notification Service**: Template storage, notification logs
- **Media Service**: File metadata with variable attributes

### Elasticsearch (1 service)
- **Search Service**: Full-text search, faceted filtering, autocomplete

### Redis
- Rate limiting (API Gateway)
- Session caching
- Product catalog caching (Product Service)
- Promotion caching (Promotion Service)

## Resilience Patterns

All inter-service calls are protected by:

1. **Circuit Breaker**: Fails fast when downstream service is unhealthy
2. **Retry**: Automatic retries with exponential backoff
3. **Bulkhead**: Limits concurrent calls to prevent resource exhaustion
4. **Time Limiter**: Enforces timeouts on slow operations

See [RESILIENCE_PATTERNS.md](RESILIENCE_PATTERNS.md) for configuration details.

## Security Architecture

```
Client Request
      │
      ▼
┌─────────────────┐
│  API Gateway    │
│  ┌───────────┐  │
│  │Rate Limit │──┼── Redis
│  │ Security  │  │
│  │ Headers   │  │
│  │IP Whitelist│ │
│  │JWT Auth   │──┼── Auth0
│  └───────────┘  │
└────────┬────────┘
         │
    Microservices
```

See [SECURITY.md](SECURITY.md) for detailed security configuration.

## Observability Stack

| Tool | Purpose | Port |
|------|---------|------|
| **Zipkin** | Distributed tracing | 9411 |
| **Prometheus** | Metrics collection | 9090 |
| **Grafana** | Dashboards & visualization | 3000 |

Key metrics:
- Request latency (p50, p95, p99)
- Error rates by service
- Circuit breaker states
- JVM metrics (memory, GC, threads)
- Virtual thread statistics

See [TRACING_SETUP.md](TRACING_SETUP.md) for tracing configuration.

## Deployment Architecture

### Local Development
- Docker Compose for infrastructure
- Maven spring-boot:run for services
- Profiles: `local` (no Auth0), `docker`

### Production (Future)
- Kubernetes deployment
- Helm charts for configuration
- Horizontal Pod Autoscaling
- Blue-green deployments

## Key Architectural Decisions

1. **Virtual Threads**: All services use Java 21 virtual threads for improved concurrency on I/O-bound operations.

2. **Service Discovery**: Eureka provides dynamic service registration and discovery.

3. **API Gateway**: Spring Cloud Gateway handles routing, rate limiting, and security.

4. **Event-Driven**: Kafka enables loose coupling and async processing.

5. **Cache-Aside**: Redis caching with explicit cache management.

6. **Database per Service**: Each service owns its data.

## Related Documentation

- [VIRTUAL_THREADS.md](VIRTUAL_THREADS.md) - Virtual threads implementation
- [RESILIENCE_PATTERNS.md](RESILIENCE_PATTERNS.md) - Circuit breakers and retry
- [CACHING_STRATEGY.md](CACHING_STRATEGY.md) - Redis caching patterns
- [SECURITY.md](SECURITY.md) - Security architecture
- [INTEGRATION_PATTERNS.md](INTEGRATION_PATTERNS.md) - Service integration
