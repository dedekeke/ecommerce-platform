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

## Documentation

| Document | Description |
|----------|-------------|
| [QUICKSTART.md](QUICKSTART.md) | Getting started guide |
| [docs/API_DOCUMENTATION.md](docs/API_DOCUMENTATION.md) | API reference |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | System architecture |
| [docs/VIRTUAL_THREADS.md](docs/VIRTUAL_THREADS.md) | Virtual threads guide |
| [docs/RESILIENCE_PATTERNS.md](docs/RESILIENCE_PATTERNS.md) | Resilience patterns |
| [docs/CACHING_STRATEGY.md](docs/CACHING_STRATEGY.md) | Caching implementation |
| [docs/SECURITY.md](docs/SECURITY.md) | Security architecture |
| [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) | Common issues |
| [scripts/README.md](scripts/README.md) | Development scripts |

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

## Upcoming
- Frontend Shell and React Micro-Frontends
- Angular Micro-Frontends
- Testing, Optimization, Documentation

See [plan.md](plan.md) for the complete development plan.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

**Built with Java 21, Spring Boot, gRPC, and Virtual Threads**
