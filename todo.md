# E-Commerce Platform - TODO & Progress Tracker

> Last updated: 2025-12-29 

---

## Completed Tasks

### Day 29 - Auth0 Integration & E2E Testing
- [x] Test API Gateway Auth0 integration with real tenant
- [x] Implement M2M (machine-to-machine) authentication
- [x] Auth0 M2M token integration verified end-to-end
- [x] Fixed API Gateway stripPrefix routing issue in `GatewayRoutesConfig.java`
- [x] Fixed MySQL localhost authentication issue for Product Service (run in Docker)
- [x] E2E Order Flow Testing - All 7 services running locally
- [x] Implement batch processing and scheduled jobs
- [x] Cart expiration & cleanup scheduled job

### Previous Days
- [x] Verify Zipkin accessibility (http://localhost:9411)
- [x] Start Redis container for caching
- [x] Start all services for testing all the flows
- [x] Start Kafka container for event messaging
- [x] Create local profile for all services to bypass Auth0 during development

---

## In Progress / High Priority
- None currently

---

## Pending Tasks (By Priority)

### Priority 1: Testing & Quality
- [x] Test API Gateway Auth0 integration with real tenant
- [x] E2E Order Flow Testing with Auth0 M2M  
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
- [x] Complete Auth0 integration end-to-end  
- [x] Implement M2M (machine-to-machine) authentication  
- [x] API Gateway JWT validation with Auth0  
- [ ] Add API key management
- [ ] Implement RBAC (Role-Based Access Control)
- [ ] Security audit with OWASP ZAP
- [ ] Secrets management (Vault/AWS Secrets Manager)
- [ ] Enable HTTPS/TLS for all services

### Priority 5: Backend Enhancements
- [ ] Implement Redis caching layer
- [x] Add rate limiting in API Gateway 
- [ ] Configure circuit breakers with Resilience4j
- [x] Implement batch processing and scheduled jobs  
- [x] Cart expiration & cleanup scheduled job  
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
- [x] Cart expiration & cleanup scheduled job
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

### Running Product Service (MySQL Issue Workaround)
## todo  
- [ ] fix this later
- Product service must run in Docker to connect to MySQL:
```bash
docker run -d --name product-service-test \
  --network ecommerce-platform_ecommerce-network \
  -v $(pwd)/services/product-service/target/product-service-1.0.0-SNAPSHOT.jar:/app/app.jar \
  -e PRODUCT_DB_URL=jdbc:mysql://ecommerce-mysql:3306/productdb \
  -e MYSQL_USER=admin -e MYSQL_PASSWORD=admin123 \
  -e SECURITY_ENABLED=false -e SERVER_PORT=8082 \
  -p 8082:8082 eclipse-temurin:21-jdk-alpine \
  java -Dsecurity.enabled=false -jar /app/app.jar
```
