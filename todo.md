# E-Commerce Platform - TODO & Progress Tracker

> Last updated: 2025-12-22

---

## Completed Tasks
- [x] Verify Zipkin accessibility (http://localhost:9411) TOP PRIORITY
- [x] Start Redis container for caching
- [x] Start all services for testing all the flows
- [x] Start Kafka container for event messaging
- [x] Create local profile for all services to bypass Auth0 during development
---

## In Progress / High Priority
### Infrastructure - Local Development
- [ ] Test API Gateway Auth0 integration with real tenant

---

## Pending Tasks (By Priority)

### Priority 1: Testing & Quality
- [ ] Increase unit test coverage to 80%+
- [ ] Add contract tests between services
- [ ] Add E2E tests with Playwright/Cypress
- [ ] Add performance/load testing suite

### Priority 2: Build & Deployment
- [ ] Implement multi-stage Docker builds (build JARs inside Docker)
- [ ] Optimize Docker layer caching
- [ ] Create production-ready docker-compose.prod.yml
- [ ] Add Docker healthcheck for all infrastructure services
- [ ] Create Kubernetes deployment manifests
- [ ] Create Helm charts
- [ ] Implement blue-green deployment strategy

### Priority 3: Observability
- [ ] Set up Prometheus metrics collection
- [ ] Create Grafana dashboards
- [ ] Configure alerting rules
- [ ] Add custom business metrics
- [ ] Implement log aggregation (ELK stack)
- [ ] Complete distributed tracing with Zipkin

### Priority 4: Security
- [ ] Complete Auth0 integration end-to-end
- [ ] Implement M2M (machine-to-machine) authentication
- [ ] Add API key management
- [ ] Implement RBAC (Role-Based Access Control)
- [ ] Security audit with OWASP ZAP
- [ ] Secrets management (Vault/AWS Secrets Manager)
- [ ] Enable HTTPS/TLS for all services

### Priority 5: Backend Enhancements
- [ ] Implement Redis caching layer
- [ ] Add rate limiting in API Gateway
- [ ] Configure circuit breakers with Resilience4j
- [ ] Implement batch processing and scheduled jobs
- [ ] Add API versioning
- [ ] Add GraphQL API layer (optional)

### Priority 6: Database Optimizations
- [ ] Add database indexes optimization
- [ ] Implement connection pooling configuration tuning
- [ ] Set up database backups
- [ ] Add read replicas for scalability
- [ ] Fix N+1 query issues

### Priority 7: Documentation
- [ ] API documentation with Swagger/OpenAPI (partially done)
- [ ] Architecture diagrams (C4 model)
- [ ] Deployment guide
- [ ] Developer onboarding guide
- [ ] Operations runbook
- [ ] Troubleshooting guide

---

## Future Enhancements (Nice to Have)

### Cart Service Enhancements
- [ ] Anonymous cart support & merge on login
- [ ] Cart expiration & cleanup scheduled job
- [ ] Price validation on checkout
- [ ] Coupon/Promotion integration
- [ ] Saved for later feature
- [ ] Cart sharing via link
- [ ] Multi-currency support

### Search Service Enhancements
- [ ] Advanced faceting (price ranges, ratings)
- [ ] Search result caching with Redis
- [ ] Search analytics and tracking
- [ ] "Did you mean" suggestions
- [ ] Fuzzy search for typo tolerance
- [ ] Multi-language support

### Media Service Enhancements
- [ ] S3/MinIO integration for production
- [ ] CDN integration (CloudFront/Cloudflare)
- [ ] Video file support with transcoding
- [ ] Batch upload support
- [ ] Image cropping and editing API

### Promotion Service Enhancements
- [ ] Order Service integration - add promotion code to orders
- [ ] Product Service integration - link promotions to categories
- [ ] Admin dashboard for promotion management
- [ ] Analytics - track promotion effectiveness

### Advanced Features
- [ ] Event sourcing for Order Service
- [ ] CQRS pattern for read-heavy services
- [ ] Feature flags system
- [ ] A/B testing framework
- [ ] Real-time notifications (WebSockets)
- [ ] Service mesh (Istio/Linkerd)

### Frontend (Future Phase)
- [ ] Shell app with Module Federation
- [ ] Auth0 frontend integration
- [ ] Product Catalog MFE (React)
- [ ] Shopping Cart MFE (React)
- [ ] Checkout MFE (React)
- [ ] User Dashboard MFE (Angular)
- [ ] Admin Dashboard MFE (Angular)

---

## Quick Reference - Running Services

### Start All Services Locally
```bash
# 1. Start infrastructure (Docker)
docker-compose up -d

# 2. Start services (in order)
java -jar infrastructure/eureka-server/target/*.jar &
java -jar infrastructure/config-server/target/*.jar &
java -jar infrastructure/api-gateway/target/*.jar &

# 3. Start business services (with local profile for cart)
java -jar -Dspring.profiles.active=local services/cart-service/target/*.jar &
java -jar services/user-service/target/*.jar &
java -jar services/product-service/target/*.jar &
java -jar services/order-service/target/*.jar &
java -jar services/inventory-service/target/*.jar &
java -jar services/notification-service/target/*.jar &
java -jar services/promotion-service/target/*.jar &
```

### Service Ports
| Service | HTTP Port | gRPC Port |
|---------|-----------|-----------|
| Eureka Server | 8761 | - |
| Config Server | 8888 | - |
| API Gateway | 8080 | - |
| User Service | 8081 | - |
| Product Service | 8082 | 9091 |
| Cart Service | 8083 | - |
| Order Service | 8084 | - |
| Inventory Service | 8086 | 9092 |
| Notification Service | 8087 | - |
| Promotion Service | 8090 | 9090 |
| Search Service | 8089 | - |
| Media Service | 8095 | - |

### Health Check URLs
- Eureka Dashboard: http://localhost:8761
- Config Server: http://localhost:8888/actuator/health
- Promotion Service: http://localhost:8090/actuator/health
- Cart Service: http://localhost:8083/actuator/health

---

## Notes
- All services use Spring Boot 3.2.0 with Java 21
- Services use virtual threads for improved concurrency
- Integration tests require all infrastructure containers running
- Cart service requires `local` profile for Auth0-less development
