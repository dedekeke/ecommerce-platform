# E-Commerce Microservices Project: 10-Week Daily Development Plan

## Progress Summary (Updated: 2026-07-14)

### Current Status: 10-Week Plan Complete — Post-Plan Hardening Phase

Day-to-day tracking has moved to [todo.md](todo.md); the forward roadmap is
[docs/SCALING_AND_IMPROVEMENTS.md](docs/SCALING_AND_IMPROVEMENTS.md). This file is the
historical plan plus the summary below.

| Week | Days | Status | Description |
|------|------|--------|-------------|
| Week 1 | Days 1-5 | **COMPLETE** | Infrastructure Foundation |
| Week 2 | Days 6-10 | **COMPLETE** | Auth0 Integration, Virtual Threads, Observability |
| Week 3 | Days 11-15 | **COMPLETE** | Core Services (User, Product, Cart) |
| Week 4 | Days 16-20 | **COMPLETE** | Transaction Services (Order, Payment, Inventory) |
| Week 5 | Days 21-25 | **COMPLETE** | Supporting Services (Notification, Search, Media, Promotion) |
| Week 6 | Days 26-30 | **COMPLETE** | Advanced Features (Caching, Security, Resilience, Scheduled Tasks, Documentation) |
| Week 7 | Days 31-35 | **COMPLETE** | Frontend Shell and Setup |
| Week 8 | Days 36-40 | **COMPLETE** | React Micro-Frontends (Product Catalog, Cart, Checkout) |
| Week 9 | Days 41-45 | **COMPLETE** | Angular Micro-Frontends (User Dashboard, Admin Dashboard) |
| Week 10 | Days 46-50 | **COMPLETE** | Testing (E2E/a11y/ZAP/coverage), K8s + Helm, CI/CD, Logging, Backup & DR |
| Post-plan | — | **IN PROGRESS** | Scaling roadmap items + hardening sprints (see todo.md) |

### Completed Milestones

**Infrastructure (Week 1-2)**
- [x] Project structure and Maven configuration
- [x] Docker Compose with PostgreSQL, MongoDB, Redis, Kafka, Elasticsearch
- [x] Eureka Server for service discovery
- [x] API Gateway with Spring Cloud Gateway
- [x] Auth0 integration with JWT validation
- [x] gRPC setup with Protocol Buffers
- [x] Virtual threads configuration (Java 21)
- [x] Distributed tracing with Zipkin
- [x] Prometheus and Grafana monitoring

**Core Services (Week 3-4)**
- [x] User Service (PostgreSQL + REST)
- [x] Product Service (PostgreSQL + REST + Redis caching)
- [x] Cart Service (MongoDB + gRPC)
- [x] Order Service (PostgreSQL + gRPC + Saga pattern)
- [x] Payment Service (PostgreSQL + gRPC)
- [x] Inventory Service (PostgreSQL + gRPC)

**Supporting Services (Week 5)**
- [x] Notification Service (MongoDB + Kafka consumer)
- [x] Search Service (Elasticsearch + Kafka sync)
- [x] Media Service (MongoDB + file storage)
- [x] Promotion Service (PostgreSQL + Redis caching)
- [x] Order-Promotion integration

**Advanced Features (Week 6)**
- [x] Redis caching layer with cache warming
- [x] Rate limiting in API Gateway
- [x] Security headers and request logging
- [x] IP whitelisting for admin endpoints
- [x] Circuit breakers with Resilience4j
- [x] Retry patterns with exponential backoff
- [x] Bulkhead for concurrent call limiting
- [x] Scheduled tasks (cart cleanup, inventory alerts, sales reports)
- [x] OpenAPI/Swagger documentation
- [x] Architecture documentation

**Frontend Development (Week 7) - Days 31-33**
- [x] Shell App initialization with Vite + React 19 + TypeScript
- [x] Module Federation configuration for micro-frontends
- [x] Auth0ProviderWithNavigate integration
- [x] ProtectedRoute, LoginButton, LogoutButton components
- [x] Testing infrastructure (Vitest + Testing Library + happy-dom)
- [x] Material-UI theme configuration (colors, typography, breakpoints)
- [x] MainLayout with Header, MobileDrawer, Footer
- [x] Responsive navigation (desktop nav links, mobile drawer)
- [x] Error boundary and loading skeletons
- [x] Zustand stores: authStore, cartStore, notificationStore, userPreferencesStore
- [x] Store persistence with Zustand persist middleware (sessionStorage/localStorage)
- [x] Devtools integration for all stores
- [x] Store selectors for optimized re-renders
- [x] useNotifications custom hook
- [x] 104 unit tests passing

**Day 34 - API Client and Interceptors (Complete)**
- [x] API client with axios (baseURL, timeout, headers, retry logic)
- [x] Request interceptor for Auth0 token injection
- [x] Response interceptor for 401/403 error handling
- [x] Request/response logging in development mode
- [x] ProductService (10 methods: getProducts, search, categories, etc.)
- [x] CartService (8 methods: getCart, addItem, sync, merge, etc.)
- [x] OrderService (8 methods: getOrders, create, cancel, track, etc.)
- [x] UserService (12 methods: profile, addresses, wishlist, etc.)
- [x] Error handling utilities with toast notifications
- [x] useApiSetup hook for Auth0 integration
- [x] 234 unit tests passing (130 new API tests)

**Day 35 - Micro-Frontend Loading Infrastructure (Complete)**
- [x] MFE types and registry configuration
- [x] MicroFrontendLoader component with React.lazy and Suspense
- [x] MFEErrorBoundary with retry functionality
- [x] useMFEPreload hook for on-hover preloading
- [x] Module loader utility with caching
- [x] Integration with App.tsx routes
- [x] 300 unit tests passing (66 new MFE tests)

**Weeks 8-10 - MFEs, Testing, Production Readiness (Complete — details in [todo.md](todo.md))**
- [x] Product Catalog, Cart, Checkout MFEs (React) + integration tests
- [x] User Dashboard and Admin Dashboard MFEs (Angular) + Shell integration
- [x] E2E (Playwright), accessibility (axe/WCAG AA), OWASP ZAP scan, 80%+ coverage
- [x] docker-compose.prod, Kubernetes (Kustomize), Helm umbrella chart, CI/CD workflows
- [x] Centralized logging (Loki), Backup & DR plan, onboarding + operations runbook

**Post-Plan Features (2026-04)**
- [x] Returns/RMA orchestration saga (order-service + notification-service)
- [x] GraphQL BFF embedded in api-gateway (`/graphql`)
- [x] Recommendation service Phase 1 (streaming co-occurrence, port 8092)
- [x] Transactional outbox (polling) in order/payment services
- [x] Real-time inventory SSE stream

**Post-Plan Hardening Sprint (2026-07-14, PRs #95-#109 — details in [todo.md](todo.md))**
- [x] Gateway: 401/committed-response fix, catalog auth-policy drift fix, response-timeout, GET-only retries
- [x] Order-service: JWT-derived identity, IDOR gaps closed, stable PageResponse envelope
- [x] Payment hardening; product/notification PageResponse envelope
- [x] Standalone reactor Dockerfiles (eureka, config-server, api-gateway)
- [x] DB connection budget (Hikari right-sizing + max_connections ceilings)
- [x] Prod log-level guard, k6 load-test suite
- [x] Autoscaling hygiene (HPAs, KEDA single-authority for order/payment, JVM heap cap)
- [x] Cart-service circuit breaker + bulkhead around product-service client

### Next Steps (Post-Plan)

1. **Scaling roadmap** — prioritized in [docs/SCALING_AND_IMPROVEMENTS.md](docs/SCALING_AND_IMPROVEMENTS.md)
   (open items include StructuredTaskScope fan-out, N+1 audit, Debezium CDC
   migration for the outbox, SLOs/error budgets, read replicas, CDN)

2. **Frontend Beautification** (ongoing, low priority — backlog in [todo.md](todo.md))
   - **Design System**: See [docs/frontend-design-brief.md](docs/frontend-design-brief.md)
   - **Use Claude Frontend Skill**: Invoke `/frontend-design` for component creation

3. **Load-test follow-ups** — validate the order-service pool size (12) and
   autoscaling ceilings under the k6 golden-path suite (`performance-tests/k6/`)

---

## Project Overview and Architecture Decisions

Based on extensive research of the YAS (Yet Another Shop) reference architecture and modern best practices for 2025, this plan delivers a production-ready e-commerce platform combining React 19, Angular micro-frontends, Java 21 Spring Boot microservices, Auth0 authentication, gRPC for internal communication, and comprehensive observability.

**Technology Stack Summary:**
- **Frontend**: React 19 (Vite) + Angular (latest) with Module Federation
- **Backend**: Java 21 Spring Boot 3.2+ with virtual threads
- **Authentication**: Auth0 (direct SPA integration, no BFF)
- **Communication**: gRPC for internal high-frequency calls, REST for public APIs
- **Infrastructure**: Docker Compose, Eureka, Spring Cloud Gateway, Kafka, PostgreSQL, MySQL, MongoDB
- **Observability**: Prometheus, Grafana, Zipkin

---

## Microservices Architecture

### Core Services (11 microservices)

1. **API Gateway** - Spring Cloud Gateway with Auth0 integration
2. **User Service** - User management, profiles (PostgreSQL + REST)
3. **Product Service** - Product catalog management (MySQL + REST) - *Read-heavy workload*
4. **Cart Service** - Shopping cart operations (MongoDB + gRPC)
5. **Order Service** - Order processing (PostgreSQL + gRPC)
6. **Payment Service** - Payment processing (PostgreSQL + gRPC)
7. **Inventory Service** - Stock management (PostgreSQL + gRPC)
8. **Notification Service** - Email/SMS notifications (MongoDB + Kafka consumer)
9. **Search Service** - Elasticsearch-based search (Elasticsearch + REST)
10. **Media Service** - Image/file management (MongoDB + REST)
11. **Promotion Service** - Discounts and promotions (MySQL + REST) - *Read-heavy workload*

### Infrastructure Services

- **Eureka Server** - Service discovery
- **Config Server** - Centralized configuration
- **Kafka + Zookeeper** - Event streaming
- **PostgreSQL** - Transactional data (User, Order, Payment, Inventory services)
- **MySQL** - Catalog data (Product, Promotion services)
- **MongoDB** - Document storage (Cart, Notification, Media services)
- **Elasticsearch** - Search engine
- **Zipkin** - Distributed tracing
- **Prometheus + Grafana** - Monitoring

### Database Selection Rationale

**PostgreSQL (4 services):**
- **User Service**: Complex user profiles with relationships, strong ACID for auth data
- **Order Service**: Financial transactions requiring strong ACID compliance
- **Payment Service**: Financial data with absolute consistency requirements
- **Inventory Service**: Advanced locking mechanisms for stock reservations

**MySQL (2 services):**
- **Product Service**: Read-heavy catalog browsing, simple data model, benefits from fast reads
- **Promotion Service**: Read-heavy validation queries, simple data model, high cache hit rate

**MongoDB (3 services):**
- **Cart Service**: Flexible schema, TTL indexes for expiration, fast reads/writes
- **Notification Service**: Template storage, flexible notification logs
- **Media Service**: File metadata with flexible attributes

---

## Week-by-Week Overview

**Week 1-2**: Infrastructure setup, project scaffolding, Auth0 configuration  
**Week 3-4**: Core backend services (User, Product, Cart)  
**Week 5**: Transaction services (Order, Payment, Inventory) with gRPC  
**Week 6**: Supporting services and advanced features  
**Week 7**: Frontend shell and React micro-frontends  
**Week 8**: More React MFEs and integration  
**Week 9**: Angular micro-frontends  
**Week 10**: Testing, optimization, documentation, deployment

---

## WEEK 1: Infrastructure Foundation (Days 1-5)

### Day 1: Project Setup and Repository Structure

**Morning (4 hours):**
- Initialize Git repository with monorepo structure
- Create parent Maven POM with dependency management
- Set up directory structure:
```
ecommerce-platform/
├── infrastructure/
│   ├── eureka-server/
│   ├── config-server/
│   ├── api-gateway/
│   └── monitoring/
├── services/
│   ├── user-service/
│   ├── product-service/
│   ├── cart-service/
│   └── [other services]/
├── frontend/
│   ├── shell-app/
│   ├── product-catalog-mfe/
│   ├── cart-mfe/
│   └── user-dashboard-mfe/
├── common-library/
├── docker/
│   ├── docker-compose.yml
│   ├── docker-compose.search.yml
│   └── docker-compose.monitoring.yml
└── docs/
```

**Afternoon (4 hours):**
- Set up common-library module with shared utilities
- Configure Maven plugins (Spring Boot, gRPC, Protobuf)
- Set up .env template file
- Create comprehensive README with setup instructions

**Deliverables:** Git repository with proper structure, parent POM, common library skeleton

---

### Day 2: Docker Compose Infrastructure

**Morning (4 hours):**
- Create comprehensive `docker-compose.yml` with PostgreSQL, MySQL, MongoDB, Kafka + Zookeeper
- Write PostgreSQL init script to create databases (userdb, orderdb, paymentdb, inventorydb)
- Write MySQL init script to create databases (productdb, promotiondb)

**Afternoon (4 hours):**
- Add monitoring stack to `docker-compose.monitoring.yml` (Prometheus, Grafana, Zipkin)
- Configure Prometheus scraping, Grafana datasources
- Add health checks to all services
- Test full infrastructure startup with both PostgreSQL and MySQL

**Deliverables:** Complete docker-compose setup with PostgreSQL + MySQL, initialization scripts, working infrastructure

---

### Day 3: Service Discovery and Config Server

**Morning (4 hours):**
- Implement Eureka Server with Java 21
- Configure for standalone mode
- Create Dockerfile
- Add to docker-compose

**Afternoon (4 hours):**
- Implement Config Server with Git backend
- Create configuration repository structure
- Implement encryption for sensitive properties
- Configure profiles (dev, docker, prod)
- Create configuration files for all planned services

**Deliverables:** Running Eureka Server (port 8761), Config Server with Git backend

---

### Day 4: API Gateway with Auth0

**Morning (4 hours):**
- Set up Spring Cloud Gateway
- Configure routes to all future microservices
- Implement TokenRelay filter for JWT propagation

**Afternoon (4 hours):**
- Create Auth0 tenant and configure
- Set up API identifier and SPA application in Auth0
- Implement OAuth2 resource server in Gateway
- Configure JWT validation (issuer, audience, signature)
- Configure CORS for SPAs
- Test token validation flow

**Deliverables:** API Gateway with Auth0 integration, JWT validation working

---

### Day 5: gRPC Setup and Testing Infrastructure

**Morning (4 hours):**
- Create proto/ directory in common-library
- Define gRPC service definitions for Cart, Order, Payment, Inventory services
- Configure gRPC Maven plugin
- Generate Java classes from proto files

**Afternoon (4 hours):**
- Set up Testcontainers for integration testing
- Create base test classes for microservices
- Configure JaCoCo for code coverage
- Set up GitHub Actions CI/CD pipeline
- Write infrastructure integration tests

**Deliverables:** gRPC proto definitions, generated classes, testing infrastructure, CI/CD pipeline

---

## WEEK 2: Auth0 Deep Integration and Observability (Days 6-10)

### Day 6: Common Security Configuration

**Morning (4 hours):**
- Create BaseSecurityConfig in common-library with JWT decoder, audience validator
- Implement custom claims extraction utilities
- Create reusable authorization configuration

**Afternoon (4 hours):**
- Implement service-to-service authentication (M2M) utility using Client Credentials flow
- Implement token caching for M2M calls with Redis
- Create security test helpers
- Document security patterns and usage

**Deliverables:** Reusable security configuration, M2M authentication utility, documentation

---

### Day 7: Virtual Threads Configuration

**Morning (4 hours):**
- Enable virtual threads globally in all services: `spring.threads.virtual.enabled=true`
- Configure async executors to use virtual threads via `Executors.newVirtualThreadPerTaskExecutor()`
- Document where virtual threads provide benefits (database calls, REST API calls, Kafka operations)

**Afternoon (4 hours):**
- Identify and replace `synchronized` blocks with `ReentrantLock` to prevent pinning
- Set up JDK Flight Recorder for pinning detection
- Create performance testing scenarios
- Document virtual thread usage guidelines and anti-patterns

**Deliverables:** Virtual threads enabled, monitoring configured, performance guidelines

---

### Day 8: Distributed Tracing Setup

**Morning (4 hours):**
- Configure OpenTelemetry in common-library
- Add Micrometer tracing dependencies
- Configure Zipkin endpoint in all services
- Set sampling probability to 1.0 for development

**Afternoon (4 hours):**
- Configure custom spans for business operations
- Add correlation IDs to all logs (MDC integration)
- Set up structured logging with JSON format
- Create trace visualization dashboards in Zipkin
- Test end-to-end tracing across services

**Deliverables:** Distributed tracing working, correlation IDs in logs, Zipkin dashboards

---

### Day 9: Kafka Event Infrastructure

**Morning (4 hours):**
- Define event schemas (OrderCreatedEvent, PaymentCompletedEvent, InventoryUpdatedEvent, etc.)
- Create event publisher utility in common-library
- Configure Kafka topics with proper partitioning
- Implement dead letter queue strategy

**Afternoon (4 hours):**
- Create base event consumer configuration
- Implement retry logic with exponential backoff
- Set up Kafka monitoring in Prometheus
- Create event serialization/deserialization utilities
- Test event flow end-to-end

**Deliverables:** Event schema definitions, Kafka infrastructure, event monitoring

---

### Day 10: Monitoring and Alerting

**Morning (4 hours):**
- Configure Spring Boot Actuator in all services (health, metrics, prometheus, info endpoints)
- Create custom metrics for business KPIs: orders per minute, cart abandonment rate, payment success rate, API response times

**Afternoon (4 hours):**
- Create comprehensive Grafana dashboards: service health, request rates/latencies, error rates, JVM metrics, business metrics
- Configure Prometheus alert rules for critical issues
- Set up notification channels (email, Slack)
- Test alerting workflow

**Deliverables:** Comprehensive Grafana dashboards, alert rules, monitoring documentation

---

## WEEK 3: Core Backend Services - User and Product (Days 11-15)

### Day 11: User Service (Part 1)

**Morning (4 hours):**
- Generate User Service from Spring Initializr (Web, Data JPA, PostgreSQL, OAuth2 Resource Server, Eureka Client, Actuator)
- Define User domain model with auth0Id, email, firstName, lastName, phoneNumber, role, defaultAddress
- Include Address embeddable object

**Afternoon (4 hours):**
- Implement JPA repository layer
- Create service layer with business logic
- Implement Auth0 user synchronization (on first login, sync Auth0 user data)
- Create basic REST controllers
- Write unit tests

**Deliverables:** User service with domain model, basic CRUD operations, unit tests

---

### Day 12: User Service (Part 2) - Complete

**Morning (4 hours):**
- Implement REST API endpoints: GET /me, PUT /me, GET /addresses, POST /addresses, PUT /addresses/{id}, DELETE /addresses/{id}
- Extract Auth0 user ID from JWT claims
- Implement profile update logic

**Afternoon (4 hours):**
- Add comprehensive validation and error handling
- Create integration tests with Testcontainers (PostgreSQL)
- Add OpenAPI/Swagger documentation annotations
- Dockerize service and add to docker-compose
- Test Eureka registration

**Deliverables:** Complete User Service with REST API, integration tests, running in Docker

---

### Day 13: Product Service (Part 1)

**Morning (4 hours):**
- Define Product domain model: id, sku, name, description, category, price, currency, images, dimensions, stockQuantity, active
- Define Category entity with hierarchical structure (parent-child relationship)
- Set up MySQL database connection and proper indexes for search optimization

**Afternoon (4 hours):**
- Implement repository with custom queries: search by name/description, filter by category, filter by price range, pagination support
- Create service layer with business logic optimized for read-heavy workload
- Implement category management logic
- Write unit tests for repository queries

**Deliverables:** Product domain model with MySQL, repository with search capabilities, service layer

---

### Day 14: Product Service (Part 2) - Complete

**Morning (4 hours):**
- Implement REST API: GET /products (with search, filters, pagination), GET /products/{id}, POST /products, PUT /products/{id}, DELETE /products/{id}
- Add admin-only authorization with Auth0 scopes
- Implement category endpoints

**Afternoon (4 hours):**
- Implement product image management (store URLs, integrate with Media Service later)
- Create integration tests with Testcontainers (MySQL)
- Publish ProductCreatedEvent and ProductUpdatedEvent to Kafka
- Add Redis caching for product details (perfect for read-heavy catalog)
- Dockerize and deploy

**Deliverables:** Complete Product Service with MySQL, event publishing, caching, running in Docker

---

### Day 15: Cart Service with gRPC

**Morning (4 hours):**
- Implement CartGrpcServiceImpl extending generated gRPC base class
- Implement methods: addItem, removeItem, updateQuantity, getCart, clearCart
- Use MongoDB for cart storage (fast reads, TTL for expiration)

**Afternoon (4 hours):**
- Define MongoDB Cart document: id, userId, items (productId, name, price, quantity), subtotal, timestamps, expiresAt
- Configure TTL index on expiresAt field (auto-delete after 7 days)
- Create REST endpoints for public API (for direct browser access)
- Implement cart merging logic (guest cart → authenticated user cart)
- Write tests for gRPC service
- Dockerize and deploy

**Deliverables:** Cart Service with gRPC and REST APIs, MongoDB integration, running in Docker

---

## WEEK 4: Transaction Services with gRPC (Days 16-20)

### Day 16: Order Service (Part 1) - Setup and Domain

**Morning (4 hours):**
- Define Order domain model: id, orderNumber, userId, items (OrderItem entities), subtotal, tax, shippingCost, total, status (enum), shippingAddress, paymentIntentId, timestamps
- Define OrderStatus enum: PENDING, CONFIRMED, PROCESSING, SHIPPED, DELIVERED, CANCELLED, REFUNDED
- Define OrderItem entity: product details snapshot, price, quantity

**Afternoon (4 hours):**
- Implement repository layer
- Create order service with business logic
- Implement order number generation (human-readable, e.g., ORD-2025-00001)
- Create order state machine (allowed status transitions)
- Write unit tests for state machine

**Deliverables:** Order domain model, repository, service layer, state machine

---

### Day 17: Order Service (Part 2) - gRPC Integration

**Morning (4 hours):**
- Define order.proto with CreateOrder, GetOrder, UpdateOrderStatus, CancelOrder methods
- Implement OrderGrpcServiceImpl
- Create gRPC clients for Cart, Inventory, Payment services

**Afternoon (4 hours):**
- Implement order creation flow:
  1. Get cart items from Cart Service (gRPC)
  2. Reserve stock in Inventory Service (gRPC)
  3. Create payment intent in Payment Service (gRPC)
  4. Create order in database
  5. Publish OrderCreatedEvent to Kafka
  6. Return order response
- Implement saga pattern with compensation logic (rollback on failure)
- Write integration tests for complete flow
- Dockerize and deploy

**Deliverables:** Order Service with gRPC, saga pattern, integration with other services

---

### Day 18: Payment Service with gRPC

**Morning (4 hours):**
- Define Payment domain model: id, orderId, userId, amount, currency, method (enum), status (enum), paymentIntentId, transactionId, timestamps
- Define PaymentStatus enum: PENDING, PROCESSING, COMPLETED, FAILED, REFUNDED
- Set up payment gateway mock (Stripe-like API)

**Afternoon (4 hours):**
- Implement PaymentGrpcServiceImpl with createPaymentIntent, confirmPayment, refundPayment methods
- Use virtual threads for I/O-bound payment gateway calls
- Implement webhook handlers for payment events
- Publish PaymentCompletedEvent and PaymentFailedEvent to Kafka
- Write tests with mocked payment gateway
- Dockerize and deploy

**Deliverables:** Payment Service with gRPC, payment gateway integration, event publishing

---

### Day 19: Inventory Service with gRPC

**Morning (4 hours):**
- Define Inventory domain model: id, productId, sku, quantity, reservedQuantity, availableQuantity (computed), status, reorderLevel, reorderQuantity, lastRestockedAt
- Define InventoryReservation entity: id, productId, orderId, quantity, status, expiresAt (15 minutes)
- Set up proper indexes for fast lookups

**Afternoon (4 hours):**
- Implement InventoryGrpcServiceImpl with reserveStock, commitReservation, releaseReservation, checkAvailability methods
- Implement reservation logic with pessimistic locking
- Create scheduled job to auto-release expired reservations (every 5 minutes)
- Add REST endpoints for inventory management
- Publish InventoryUpdatedEvent and StockLowEvent to Kafka
- Write integration tests
- Dockerize and deploy

**Deliverables:** Inventory Service with gRPC, reservation logic, scheduled jobs

---

### Day 20: Service Integration Testing

**Full Day (8 hours):**

**Morning:**
- Write comprehensive end-to-end integration tests for complete order flow:
  1. Create user via User Service
  2. Create products via Product Service
  3. Add items to cart via Cart Service (gRPC)
  4. Create order via Order Service (gRPC)
  5. Verify inventory reserved
  6. Complete payment via Payment Service
  7. Verify order status updates
  8. Verify events published

**Afternoon:**
- Test saga compensation scenarios (payment failure, insufficient stock)
- Test concurrent order creation (race conditions)
- Performance testing with virtual threads (measure throughput improvement)
- Load testing with JMeter (1000 concurrent orders)
- Document integration patterns and API contracts
- Fix any discovered issues

**Deliverables:** Full integration test suite, performance results, integration documentation

---

## WEEK 5: Supporting Services (Days 21-25)

### Day 21: Notification Service

**Morning (4 hours):**
- Setup Notification Service with MongoDB for templates and logs
- Define NotificationTemplate document and NotificationLog document
- Integrate email service (SendGrid/AWS SES mock)
- Integrate SMS service (Twilio mock)

**Afternoon (4 hours):**
- Implement Kafka event consumers for OrderCreatedEvent, PaymentCompletedEvent, OrderShippedEvent
- Create notification templates with Thymeleaf (order confirmation, payment receipt, shipping notification)
- Implement retry logic for failed notifications
- Create REST API for notification history
- Write tests
- Dockerize and deploy

**Deliverables:** Notification Service with email/SMS, event-driven triggers, template system

---

### Day 22: Search Service with Elasticsearch

**Morning (4 hours):**
- Add Elasticsearch to docker-compose
- Define product index mapping with proper analyzers for full-text search
- Configure Spring Data Elasticsearch

**Afternoon (4 hours):**
- Implement ProductSearchService with advanced search features: full-text search, filters (category, price range), faceted search, autocomplete
- Implement Kafka consumer to sync from ProductCreatedEvent and ProductUpdatedEvent
- Create REST API endpoints
- Add aggregations for categories and price ranges
- Write integration tests with Testcontainers Elasticsearch
- Dockerize and deploy

**Deliverables:** Search Service with Elasticsearch, real-time indexing, advanced search

---

### Day 23: Media Service

**Morning (4 hours):**
- Setup Media Service with MongoDB for metadata
- Define Media document: id, filename, contentType, size, storageUrl, thumbnailUrl, dimensions, uploadedBy
- Configure file storage (local or S3 mock)

**Afternoon (4 hours):**
- Implement REST API: POST /upload (with multipart file), GET /{id}, DELETE /{id}
- Implement image resizing and thumbnail generation (Java ImageIO or Thumbnailator library)
- Add file validation (size limits, allowed types)
- Implement access control (only uploader or admin can delete)
- Write tests
- Dockerize and deploy

**Deliverables:** Media Service with image upload, optimization, access control

---

### Day 24: Promotion Service

**Morning (4 hours):**
- Define Promotion domain model: id, code, name, description, type (PERCENTAGE, FIXED_AMOUNT, BUY_X_GET_Y), discountValue, minPurchaseAmount, maxUses, currentUses, startDate, endDate, active, applicableCategories
- Set up MySQL database connection and implement repository with unique constraint on code

**Afternoon (4 hours):**
- Implement promotion validation logic (check dates, usage limits, minimum purchase)
- Implement discount calculation logic for different promotion types
- Create REST API: GET /promotions (public), GET /promotions/{code}/validate, POST /promotions (admin), PUT /promotions/{id} (admin)
- Add Redis caching for active promotions (perfect for read-heavy validation queries)
- Integrate with Order Service (add promotion code field to order)
- Write tests with Testcontainers (MySQL)
- Dockerize and deploy

**Deliverables:** Promotion Service with MySQL, discount logic, admin API, integration ready

---

### Day 25: Integration and Refinement

**Morning (4 hours):**
- Complete Order Service integration with Promotion Service (apply discount during order creation)
- Update order creation flow to include promotion validation and calculation
- Test complete order flow with promotions

**Afternoon (4 hours):**
- Performance optimization across services: database query optimization, add missing indexes, implement query result caching
- Configure gRPC connection pooling
- Update API Gateway routes for all new services
- Consolidate Swagger documentation at Gateway level
- Update Grafana dashboards with new service metrics
- Document all API endpoints and integration patterns

**Deliverables:** All backend services integrated, optimized, fully documented

---

## WEEK 6: Advanced Backend Features (Days 26-30)

### Day 26: Redis Caching Layer

**Morning (4 hours):**
- Add Redis to docker-compose
- Configure Spring Cache in common-library with Redis backend
- Define cache configurations: products (1 hour TTL), promotions (30 min TTL), user profiles (15 min TTL)

**Afternoon (4 hours):**
- Implement cache-aside pattern in Product Service with @Cacheable, @CachePut, @CacheEvict
- Add cache warming on startup (preload popular products)
- Implement cache eviction strategy (on product updates)
- Add Redis monitoring to Grafana
- Test cache hit rates and performance improvement
- Document caching strategy

**Deliverables:** Redis caching layer, optimized cache strategies, monitoring

---

### Day 27: Rate Limiting and Security

**Morning (4 hours):**
- Configure Redis-based rate limiting in API Gateway (10 requests/sec per user, 20 burst capacity)
- Apply rate limiting to all routes with different limits for public vs authenticated endpoints
- Implement custom rate limit exceeded response

**Afternoon (4 hours):**
- Implement additional security measures: CSRF protection in Gateway, request/response logging (excluding sensitive data), IP whitelisting for admin endpoints
- Add security headers (X-Frame-Options, X-Content-Type-Options, HSTS)
- Run security audit with OWASP ZAP
- Document security architecture

**Deliverables:** Rate limiting configured, enhanced security, security audit completed

---

### Day 28: Circuit Breaker and Resilience

**Morning (4 hours):**
- Add Resilience4j dependencies to all services
- Configure circuit breakers for external calls: Payment Service → gateway, Order Service → Payment/Inventory, Product Service → Search
- Define circuit breaker thresholds (failure rate, slow call rate)

**Afternoon (4 hours):**
- Implement fallback methods for critical operations
- Configure retry strategies with exponential backoff
- Add timeout configurations for all external calls
- Test failure scenarios (simulate service down)
- Monitor circuit breaker states in Grafana
- Document resilience patterns

**Deliverables:** Circuit breakers configured, resilience patterns implemented, failure testing

---

### Day 29: Batch Processing and Scheduled Jobs

**Morning (4 hours):**
- Implement scheduled jobs across services:
  - Cart Service: cleanup expired carts (daily at 2 AM)
  - Inventory Service: release expired reservations (every 15 minutes)
  - Search Service: full reindex (hourly)
  - Order Service: mark abandoned orders (daily)

**Afternoon (4 hours):**
- Implement batch report generation (daily sales report, inventory levels report)
- Create inventory restock alerts (when stock below reorder level)
- Implement abandoned cart recovery (email notification after 24 hours)
- Add job monitoring and alerting (if job fails, alert ops team)
- Write tests for scheduled jobs
- Document all scheduled tasks

**Deliverables:** Scheduled jobs framework, batch processing, monitoring

---

### Day 30: Backend Documentation and Review

**Morning (4 hours):**
- Complete OpenAPI/Swagger documentation for all services with comprehensive examples
- Create architecture diagrams: system context, container diagram, component diagrams
- Document deployment procedures
- Create troubleshooting guide

**Afternoon (4 hours):**
- Conduct comprehensive code review across all services
- Run security audit and address findings
- Performance profiling with JProfiler or VisualVM
- Database optimization (analyze slow queries, add indexes)
- Update all README files with current information
- Create developer onboarding guide

**Deliverables:** Complete backend documentation, architecture diagrams, optimized codebase

---

## WEEK 7: Frontend Shell and Setup (Days 31-35)

### Day 31: Frontend Shell Application (React 19)

**Morning (4 hours):**
- Initialize Shell App with Vite: `npm create vite@latest shell-app -- --template react-ts`
- Install dependencies: `@auth0/auth0-react`, `zustand`, `react-router-dom`, `@originjs/vite-plugin-federation`
- Configure Module Federation in vite.config.ts to expose remotes (productCatalog, cart, userDashboard, checkout)

**Afternoon (4 hours):**
- Implement Auth0Provider wrapper in main.tsx with correct domain, clientId, audience, scope
- Configure cacheLocation="memory" and useRefreshTokens=true for security
- Create auth context wrapper
- Implement protected route component
- Create login/logout components
- Test Auth0 flow

**Deliverables:** Shell app with Module Federation, Auth0 integration, routing

---

### Day 32: Shell App - Layout and Navigation

**Morning (4 hours):**
- Install Material-UI: `@mui/material`, `@emotion/react`, `@emotion/styled`, `@mui/icons-material`
- Create MainLayout component with AppBar, Toolbar, responsive navigation
- Implement header with logo, search bar, navigation links, cart icon (with item count), user menu

**Afternoon (4 hours):**
- Create responsive navigation (mobile drawer, desktop menu)
- Implement footer with links and information
- Create loading states and skeleton screens
- Implement global error boundary
- Configure Material-UI theme (colors, typography, breakpoints)
- Make layout responsive

**Deliverables:** Complete shell layout, responsive design, theme system

---

### Day 33: Zustand Global State Management

**Morning (4 hours):**
- Create Zustand stores:
  - authStore: token, user, setAuth, clearAuth (persisted to sessionStorage)
  - cartStore: items, total, addItem, removeItem, updateQuantity, clearCart
  - notificationStore: notifications, addNotification, removeNotification
  - userPreferencesStore: theme, language, currency

**Afternoon (4 hours):**
- Implement store persistence with Zustand persist middleware
- Configure store devtools for debugging
- Create store selectors for optimized re-renders
- Write tests for store actions and selectors
- Document store usage patterns

**Deliverables:** Complete Zustand store architecture, persistence, tests

---

### Day 34: API Client and Interceptors

**Morning (4 hours):**
- Create API client with axios, configured with baseURL from env
- Implement request interceptor to add Auth0 token to all requests
- Implement response interceptor for error handling (401 → redirect to login, 403 → show forbidden)
- Add request/response logging in development

**Afternoon (4 hours):**
- Create service classes: ProductService, CartService, OrderService, UserService
- Implement error handling utilities (toast notifications)
- Add retry logic for failed requests (using axios-retry)
- Implement request caching where appropriate
- Write integration tests
- Document API client usage

**Deliverables:** Complete API client, service layer, error handling

---

### Day 35: Micro-Frontend Loading Infrastructure

**Morning (4 hours):**
- Create MicroFrontendLoader component with React.lazy and Suspense
- Implement dynamic module loading with error handling
- Create loading fallback UI (skeleton screens)
- Create error fallback UI (retry button)

**Afternoon (4 hours):**
- Create error boundaries specific to micro-frontends
- Implement module preloading strategy (on hover)
- Create micro-frontend registry configuration
- Test module loading/unloading/error scenarios
- Implement fallback to cached version if remote fails
- Document micro-frontend integration

**Deliverables:** Micro-frontend loading infrastructure, error handling, documentation

---

## WEEK 8: React Micro-Frontends (Days 36-40)

### Day 36: Product Catalog Micro-Frontend

**Morning (4 hours):**
- Initialize Product Catalog MFE: `npm create vite@latest product-catalog-mfe -- --template react-ts`
- Install dependencies and configure Module Federation as remote
- Expose ProductList, ProductDetail, ProductSearch components
- Configure dev server on port 5001

**Afternoon (4 hours):**
- Implement ProductList component with grid layout
- Create ProductCard component with image, name, price, "Add to Cart" button
- Implement product filtering (by category, price range)
- Add pagination with Material-UI Pagination component
- Integrate with Product Service API
- Test standalone and integrated with shell

**Deliverables:** Product Catalog MFE with list view, filtering, pagination

---

### Day 37: Product Detail and Search

**Morning (4 hours):**
- Implement ProductDetail component with image gallery, product information, price, description, specifications
- Add quantity selector and "Add to Cart" button
- Integrate with Cart Service to add items
- Update cart store when items added
- Show success notification

**Afternoon (4 hours):**
- Implement ProductSearch component with autocomplete
- Use debounced search (300ms delay)
- Integrate with Search Service API
- Display search results with highlighting
- Add search suggestions
- Create product recommendations section (based on category)
- Test all interactions

**Deliverables:** Product detail view with full functionality, search with autocomplete

---

### Day 38: Shopping Cart Micro-Frontend

**Morning (4 hours):**
- Initialize Cart MFE: `npm create vite@latest cart-mfe -- --template react-ts`
- Configure Module Federation, expose Cart and CartSummary components
- Create Cart component with list of items (image, name, price, quantity controls, remove button)
- Implement quantity update and remove item functionality

**Afternoon (4 hours):**
- Create CartSummary component (subtotal, tax estimate, total)
- Implement promotion code input and validation
- Add "Proceed to Checkout" button
- Create empty cart state with call-to-action
- Integrate with Cart Service API and cartStore
- Add optimistic UI updates
- Test all cart operations

**Deliverables:** Shopping Cart MFE with full cart management functionality

---

### Day 39: Checkout Micro-Frontend (React)

**Morning (4 hours):**
- Initialize Checkout MFE as React application
- Expose CheckoutFlow component
- Create multi-step checkout: Shipping Address, Payment Method, Review Order, Confirmation
- Implement step navigation with Material-UI Stepper

**Afternoon (4 hours):**
- Implement ShippingAddress form with validation
- Implement PaymentMethod selection (credit card, PayPal)
- Create OrderReview component showing all order details
- Integrate with Order Service API
- Handle order creation and show confirmation page
- Implement error handling for payment failures
- Test complete checkout flow

**Deliverables:** Checkout MFE with complete checkout flow

---

### Day 40: React MFEs Integration and Testing

**Morning (4 hours):**
- Integrate all React MFEs into shell app
- Configure routes for each MFE
- Test navigation between MFEs
- Verify state sharing via Zustand
- Test Auth0 token passing to MFEs

**Afternoon (4 hours):**
- Implement communication between MFEs via custom events
- Test complete user journey: browse products → add to cart → checkout → confirmation
- Performance testing (lazy loading, bundle sizes)
- Optimize bundle splitting
- Write E2E tests with Playwright
- Document MFE architecture

**Deliverables:** All React MFEs integrated, E2E tests, performance optimized

---

## WEEK 9: Angular Micro-Frontends (Days 41-45)

### Day 41: User Dashboard MFE (Angular) - Setup

**Morning (4 hours):**
- Initialize Angular application: `ng new user-dashboard-mfe`
- Install dependencies: `@angular/material`, `@auth0/auth0-angular`
- Configure Module Federation with Webpack (Angular uses Webpack, not Vite)
- Set up module-federation.config.js

**Afternoon (4 hours):**
- Configure Auth0 in Angular app
- Set up Angular Material theming to match shell app
- Create base layout for user dashboard
- Set up routing for dashboard sections
- Configure Angular services structure
- Test standalone Angular app

**Deliverables:** Angular MFE project setup, Auth0 configured, base layout

---

### Day 42: User Profile and Order History

**Morning (4 hours):**
- Implement UserProfile component to display and edit user information
- Create profile form with reactive forms and validation
- Integrate with User Service API
- Implement address management (add, edit, delete addresses)

**Afternoon (4 hours):**
- Implement OrderHistory component with list of past orders
- Create OrderDetail view with full order information
- Add order status tracking visualization
- Implement order filtering and sorting
- Integrate with Order Service API
- Add pagination

**Deliverables:** User profile management, order history with details

---

### Day 43: Wishlist and Preferences (Angular)

**Morning (4 hours):**
- Implement Wishlist feature in Angular
- Create wishlist service to manage favorites
- Display wishlist items with option to add to cart
- Sync wishlist with backend (new endpoint in User Service)

**Afternoon (4 hours):**
- Implement user preferences page (notification settings, display preferences)
- Create notification history view
- Implement email subscription management
- Add preference saving/loading
- Test all dashboard features

**Deliverables:** Wishlist functionality, user preferences management

---

### Day 44: Admin Dashboard MFE (Angular)

**Morning (4 hours):**
- Create Admin Dashboard MFE (new Angular app)
- Configure Module Federation
- Implement admin authentication check (require admin role from Auth0)
- Create admin layout with sidebar navigation

**Afternoon (4 hours):**
- Implement Product Management view (list, create, edit, delete products)
- Create product form with image upload
- Implement Order Management view (list orders, update status)
- Add basic analytics dashboard (total orders, revenue, top products)
- Integrate with backend admin endpoints

**Deliverables:** Admin dashboard with product and order management

---

### Day 45: Angular MFEs Integration

**Morning (4 hours):**
- Integrate User Dashboard MFE into shell app
- Integrate Admin Dashboard MFE (admin route)
- Configure routing and lazy loading
- Test authentication flow in Angular MFEs
- Verify token propagation from shell to Angular

**Afternoon (4 hours):**
- Test inter-framework communication (React shell ↔ Angular MFEs)
- Implement event bus for cross-MFE communication
- Style consistency check across all MFEs
- Performance testing of Angular MFEs
- Write integration tests
- Document Angular MFE integration

**Deliverables:** Angular MFEs fully integrated, cross-framework communication working

---

## WEEK 10: Testing, Optimization, and Deployment (Days 46-50)

### Day 46: Comprehensive Testing

**Morning (4 hours):**
- Run full test suite for all backend services (unit + integration tests)
- Fix any failing tests
- Achieve target code coverage (80%+)
- Run mutation testing to verify test quality

**Afternoon (4 hours):**
- Run E2E tests for all user flows
- Test error scenarios and edge cases
- Perform accessibility testing (WCAG AA compliance)
- Test on multiple browsers (Chrome, Firefox, Safari, Edge)
- Test responsive design on mobile devices
- Document test results

**Deliverables:** All tests passing, comprehensive test coverage, accessibility compliance

---

### Day 47: Performance Optimization

**Morning (4 hours):**
- Backend performance optimization:
  - Analyze database query performance, add missing indexes
  - Optimize N+1 queries with proper JOIN FETCH
  - Review virtual thread usage and remove any synchronized blocks
  - Tune gRPC connection pools and thread pools
  - Optimize Kafka consumer configurations

**Afternoon (4 hours):**
- Frontend performance optimization:
  - Analyze bundle sizes, implement code splitting
  - Optimize images (lazy loading, WebP format)
  - Implement service worker for caching
  - Optimize MFE loading (preload critical MFEs)
  - Reduce initial load time (lighthouse score 90+)
- Run load testing with k6 or JMeter (1000-5000 concurrent users)
- Document performance benchmarks

**Deliverables:** Optimized application, performance benchmarks, load test results

---

### Day 48: Security Audit and Hardening

**Morning (4 hours):**
- Run comprehensive security audit:
  - OWASP dependency check
  - Static code analysis (SonarQube)
  - Security scanning with Snyk
  - Penetration testing with OWASP ZAP
- Review Auth0 configuration for security best practices

**Afternoon (4 hours):**
- Fix identified security vulnerabilities
- Implement security headers across all services
- Review and harden Docker configurations
- Implement secrets management strategy (not hardcoded)
- Enable HTTPS for all communications
- Document security architecture and compliance
- Create security runbook

**Deliverables:** Security audit completed, vulnerabilities fixed, hardened configuration

---

### Day 49: Documentation and Deployment Preparation

**Morning (4 hours):**
- Complete all documentation:
  - System architecture documentation with diagrams (C4 model)
  - API documentation (OpenAPI/Swagger consolidated)
  - Deployment guide (step-by-step instructions)
  - Developer setup guide
  - User manual
  - Operations runbook
- Create video tutorials for key workflows

**Afternoon (4 hours):**
- Prepare production deployment:
  - Create production docker-compose.yml
  - Configure production environment variables
  - Set up database migration scripts
  - Configure production logging (centralized)
  - Set up backup and disaster recovery procedures
  - Create monitoring alerts for production
- Test production deployment in staging environment

**Deliverables:** Complete documentation, production-ready deployment artifacts

---

### Day 50: Final Deployment and Handover

**Morning (4 hours):**
- Execute production deployment:
  - Deploy all microservices
  - Run database migrations
  - Verify all services healthy
  - Configure DNS and load balancers
  - Enable monitoring and alerts
- Smoke testing in production

**Afternoon (4 hours):**
- Final verification:
  - Test all critical user flows in production
  - Verify monitoring and alerting working
  - Verify backup procedures
  - Load testing in production (gradual ramp-up)
- Conduct team handover session
- Create post-deployment checklist
- Celebrate launch! 🎉

**Deliverables:** Application deployed to production, monitoring active, team trained

---

## Complete Docker Compose Configuration

```yaml
version: '3.8'

services:
  # Infrastructure Services
  
  postgres:
    image: postgres:14-alpine
    container_name: postgres
    ports:
      - "5432:5432"
    environment:
      POSTGRES_USER: admin
      POSTGRES_PASSWORD: admin123
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./scripts/init-databases.sql:/docker-entrypoint-initdb.d/init.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U admin"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - ecommerce-network

  mongodb:
    image: mongo:7-jammy
    container_name: mongodb
    ports:
      - "27017:27017"
    environment:
      MONGO_INITDB_ROOT_USERNAME: admin
      MONGO_INITDB_ROOT_PASSWORD: admin123
    volumes:
      - mongodb-data:/data/db
    healthcheck:
      test: ["CMD", "mongosh", "--eval", "db.adminCommand('ping')"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - ecommerce-network

  redis:
    image: redis:7-alpine
    container_name: redis
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - ecommerce-network

  zookeeper:
    image: confluentinc/cp-zookeeper:7.4.0
    container_name: zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    volumes:
      - zookeeper-data:/var/lib/zookeeper/data
    healthcheck:
      test: ["CMD", "nc", "-z", "localhost", "2181"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - ecommerce-network

  kafka:
    image: confluentinc/cp-kafka:7.4.0
    container_name: kafka
    depends_on:
      zookeeper:
        condition: service_healthy
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'true'
    volumes:
      - kafka-data:/var/lib/kafka/data
    healthcheck:
      test: ["CMD", "kafka-broker-api-versions", "--bootstrap-server", "localhost:9092"]
      interval: 10s
      timeout: 10s
      retries: 5
      start_period: 40s
    networks:
      - ecommerce-network

  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.11.0
    container_name: elasticsearch
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    ports:
      - "9200:9200"
    volumes:
      - elasticsearch-data:/usr/share/elasticsearch/data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9200/_cluster/health"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - ecommerce-network

  # Service Discovery & Configuration
  
  eureka-server:
    build: ./infrastructure/eureka-server
    container_name: eureka-server
    ports:
      - "8761:8761"
    environment:
      SPRING_PROFILES_ACTIVE: docker
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8761/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 40s
    networks:
      - ecommerce-network

  config-server:
    build: ./infrastructure/config-server
    container_name: config-server
    depends_on:
      eureka-server:
        condition: service_healthy
    ports:
      - "8888:8888"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8888/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 40s
    networks:
      - ecommerce-network

  # API Gateway
  
  api-gateway:
    build: ./infrastructure/api-gateway
    container_name: api-gateway
    depends_on:
      eureka-server:
        condition: service_healthy
      config-server:
        condition: service_healthy
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_CLOUD_CONFIG_URI: http://config-server:8888
      AUTH0_DOMAIN: ${AUTH0_DOMAIN}
      AUTH0_AUDIENCE: ${AUTH0_AUDIENCE}
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s
    networks:
      - ecommerce-network

  # Business Services
  
  user-service:
    build: ./services/user-service
    container_name: user-service
    depends_on:
      postgres:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
      kafka:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/userdb
      SPRING_DATASOURCE_USERNAME: admin
      SPRING_DATASOURCE_PASSWORD: admin123
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
      AUTH0_DOMAIN: ${AUTH0_DOMAIN}
      AUTH0_AUDIENCE: ${AUTH0_AUDIENCE}
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s
    networks:
      - ecommerce-network

  product-service:
    build: ./services/product-service
    container_name: product-service
    depends_on:
      postgres:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
      kafka:
        condition: service_healthy
      redis:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/productdb
      SPRING_DATASOURCE_USERNAME: admin
      SPRING_DATASOURCE_PASSWORD: admin123
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
      SPRING_DATA_REDIS_HOST: redis
      AUTH0_DOMAIN: ${AUTH0_DOMAIN}
      AUTH0_AUDIENCE: ${AUTH0_AUDIENCE}
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8082/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s
    networks:
      - ecommerce-network

  cart-service:
    build: ./services/cart-service
    container_name: cart-service
    depends_on:
      mongodb:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
    ports:
      - "9090:9090"  # gRPC port
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATA_MONGODB_URI: mongodb://admin:admin123@mongodb:27017/cartdb?authSource=admin
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      GRPC_SERVER_PORT: 9090
      AUTH0_DOMAIN: ${AUTH0_DOMAIN}
      AUTH0_AUDIENCE: ${AUTH0_AUDIENCE}
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8083/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s
    networks:
      - ecommerce-network

  order-service:
    build: ./services/order-service
    container_name: order-service
    depends_on:
      postgres:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
      kafka:
        condition: service_healthy
    ports:
      - "9091:9091"  # gRPC port
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/orderdb
      SPRING_DATASOURCE_USERNAME: admin
      SPRING_DATASOURCE_PASSWORD: admin123
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
      GRPC_SERVER_PORT: 9091
      GRPC_CLIENT_CART_SERVICE: static://cart-service:9090
      GRPC_CLIENT_PAYMENT_SERVICE: static://payment-service:9092
      GRPC_CLIENT_INVENTORY_SERVICE: static://inventory-service:9093
      AUTH0_DOMAIN: ${AUTH0_DOMAIN}
      AUTH0_AUDIENCE: ${AUTH0_AUDIENCE}
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8084/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s
    networks:
      - ecommerce-network

  payment-service:
    build: ./services/payment-service
    container_name: payment-service
    depends_on:
      postgres:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
      kafka:
        condition: service_healthy
    ports:
      - "9092:9092"  # gRPC port
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/paymentdb
      SPRING_DATASOURCE_USERNAME: admin
      SPRING_DATASOURCE_PASSWORD: admin123
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
      GRPC_SERVER_PORT: 9092
      AUTH0_DOMAIN: ${AUTH0_DOMAIN}
      AUTH0_AUDIENCE: ${AUTH0_AUDIENCE}
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8085/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s
    networks:
      - ecommerce-network

  inventory-service:
    build: ./services/inventory-service
    container_name: inventory-service
    depends_on:
      postgres:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
      kafka:
        condition: service_healthy
    ports:
      - "9093:9093"  # gRPC port
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/inventorydb
      SPRING_DATASOURCE_USERNAME: admin
      SPRING_DATASOURCE_PASSWORD: admin123
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
      GRPC_SERVER_PORT: 9093
      AUTH0_DOMAIN: ${AUTH0_DOMAIN}
      AUTH0_AUDIENCE: ${AUTH0_AUDIENCE}
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8086/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s
    networks:
      - ecommerce-network

  notification-service:
    build: ./services/notification-service
    container_name: notification-service
    depends_on:
      mongodb:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
      kafka:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATA_MONGODB_URI: mongodb://admin:admin123@mongodb:27017/notificationdb?authSource=admin
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
      AUTH0_DOMAIN: ${AUTH0_DOMAIN}
      AUTH0_AUDIENCE: ${AUTH0_AUDIENCE}
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8087/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s
    networks:
      - ecommerce-network

  search-service:
    build: ./services/search-service
    container_name: search-service
    depends_on:
      elasticsearch:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
      kafka:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_ELASTICSEARCH_URIS: http://elasticsearch:9200
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
      AUTH0_DOMAIN: ${AUTH0_DOMAIN}
      AUTH0_AUDIENCE: ${AUTH0_AUDIENCE}
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8088/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s
    networks:
      - ecommerce-network

  media-service:
    build: ./services/media-service
    container_name: media-service
    depends_on:
      mongodb:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATA_MONGODB_URI: mongodb://admin:admin123@mongodb:27017/mediadb?authSource=admin
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      AUTH0_DOMAIN: ${AUTH0_DOMAIN}
      AUTH0_AUDIENCE: ${AUTH0_AUDIENCE}
    volumes:
      - media-storage:/app/storage
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8089/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s
    networks:
      - ecommerce-network

  promotion-service:
    build: ./services/promotion-service
    container_name: promotion-service
    depends_on:
      postgres:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
      redis:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/promotiondb
      SPRING_DATASOURCE_USERNAME: admin
      SPRING_DATASOURCE_PASSWORD: admin123
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_DATA_REDIS_HOST: redis
      AUTH0_DOMAIN: ${AUTH0_DOMAIN}
      AUTH0_AUDIENCE: ${AUTH0_AUDIENCE}
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8090/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s
    networks:
      - ecommerce-network

  # Monitoring Services
  
  zipkin:
    image: openzipkin/zipkin:latest
    container_name: zipkin
    ports:
      - "9411:9411"
    environment:
      STORAGE_TYPE: mem
    healthcheck:
      test: ["CMD", "wget", "--spider", "http://localhost:9411/health"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - ecommerce-network

  prometheus:
    image: prom/prometheus:latest
    container_name: prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./config/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
    healthcheck:
      test: ["CMD", "wget", "--spider", "http://localhost:9090/-/healthy"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - ecommerce-network

  grafana:
    image: grafana/grafana:latest
    container_name: grafana
    depends_on:
      prometheus:
        condition: service_healthy
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_USER: admin
      GF_SECURITY_ADMIN_PASSWORD: admin
    volumes:
      - grafana-data:/var/lib/grafana
      - ./config/grafana/dashboards:/etc/grafana/provisioning/dashboards
      - ./config/grafana/datasources:/etc/grafana/provisioning/datasources
    healthcheck:
      test: ["CMD", "wget", "--spider", "http://localhost:3000/api/health"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - ecommerce-network

volumes:
  postgres-data:
  mongodb-data:
  redis-data:
  zookeeper-data:
  kafka-data:
  elasticsearch-data:
  prometheus-data:
  grafana-data:
  media-storage:

networks:
  ecommerce-network:
    driver: bridge
```

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

## Project Success Metrics

**Performance Targets:**
- API response time: p95 < 200ms
- gRPC call latency: p95 < 50ms
- Frontend initial load: < 3 seconds
- Lighthouse score: > 90

**Scalability Targets:**
- Support 1000+ concurrent users
- Handle 100+ orders per minute
- Process 10,000+ products

**Quality Targets:**
- Code coverage: > 80%
- Zero critical security vulnerabilities
- WCAG AA accessibility compliance
- 99.9% uptime

## Conclusion

This 10-week plan delivers a production-ready, modern e-commerce platform leveraging cutting-edge technologies. The architecture combines the performance benefits of Java 21 virtual threads, the efficiency of gRPC for internal communication, the flexibility of micro-frontends with React 19 and Angular, and the robustness of Auth0 authentication. The YAS reference architecture provides proven patterns, while the specific enhancements (gRPC, virtual threads, micro-frontends) position this platform for high scalability and maintainability.