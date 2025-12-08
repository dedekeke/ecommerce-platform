# TODO - Future Enhancements

## Build & Docker
- [ ] Implement multi-stage Docker builds (build JARs inside Docker)
- [ ] Add .dockerignore files for each service
- [ ] Optimize Docker layer caching
- [ ] Create production-ready docker-compose.prod.yml
- [ ] Add Docker healthcheck for all infrastructure services

## Services - Missing Implementation
- [ ] Notification Service (email/SMS)
- [ ] Search Service (Elasticsearch)
- [ ] Media Service (image upload/storage)
- [ ] Promotion Service (discounts/coupons)

## Infrastructure Services - Verify & Test
- [ ] Test Eureka Server registration
- [ ] Test Config Server configuration
- [ ] Test API Gateway routing and Auth0 integration
- [ ] Verify service discovery works end-to-end

## Integration Tests
- [x] Fix basic integration tests (30/37 passing - 81% success rate)
- [x] Fix user creation endpoint and ID type issues
- [x] Fix product service endpoint mapping and ID types
- [x] Fix cart service endpoints and ID type consistency
- [x] Fix order service REST endpoints
- [ ] **Fix Cart Service Validation Issues** (3 failing tests)
  - [ ] Fix cart add item 400 errors - missing product details validation
  - [ ] Implement proper product availability checking when adding to cart
  - [ ] Fix cart item update validation
- [ ] **Fix Order Creation Authentication** (2 failing tests)
  - [ ] Resolve 401 unauthorized errors in order creation flow
  - [ ] Implement proper authentication token handling for test scenarios
  - [ ] Add test-friendly authentication bypass for integration tests
- [ ] **Fix Saga Compensation Tests** (2 failing tests)
  - [ ] Fix insufficient stock order creation test
  - [ ] Fix payment failure inventory release compensation
  - [ ] Fix order cancellation compensation flow
  - [ ] Verify distributed transaction rollback mechanisms
- [ ] Fix JMeter API compatibility issues
- [ ] Add contract tests between services
- [ ] Add chaos engineering tests
- [ ] Implement full load testing suite
- [ ] Add security penetration tests

## Backend Enhancements
- [ ] Implement Redis caching layer (Day 26)
- [ ] Add rate limiting in API Gateway (Day 27)
- [ ] Configure circuit breakers with Resilience4j (Day 28)
- [ ] Implement batch processing and scheduled jobs (Day 29)
- [ ] Add distributed caching
- [ ] Implement API versioning
- [ ] Add GraphQL API layer

## Observability
- [ ] Set up Prometheus metrics collection
- [ ] Create Grafana dashboards
- [ ] Configure alerting rules
- [ ] Add custom business metrics
- [ ] Implement log aggregation (ELK stack)

## Security
- [ ] Complete Auth0 integration end-to-end
- [ ] Implement M2M authentication
- [ ] Add API key management
- [ ] Implement RBAC (Role-Based Access Control)
- [ ] Security audit with OWASP ZAP
- [ ] Secrets management (Vault/AWS Secrets Manager)
- [ ] Enable HTTPS/TLS

## Database
- [ ] Create database migration scripts (Flyway/Liquibase)
- [ ] Add database indexes optimization
- [ ] Implement connection pooling configuration
- [ ] Set up database backups
- [ ] Add read replicas for scalability

## Frontend (Week 7-9)
- [ ] Shell app with Module Federation
- [ ] Auth0 frontend integration
- [ ] Product Catalog MFE (React)
- [ ] Shopping Cart MFE (React)
- [ ] Checkout MFE (React)
- [ ] User Dashboard MFE (Angular)
- [ ] Admin Dashboard MFE (Angular)

## Testing
- [ ] Increase unit test coverage to 80%+
- [ ] Add mutation testing
- [ ] E2E tests with Playwright/Cypress
- [ ] Accessibility testing (WCAG AA)
- [ ] Browser compatibility testing

## Documentation
- [ ] API documentation with Swagger/OpenAPI
- [ ] Architecture diagrams (C4 model)
- [ ] Deployment guide
- [ ] Developer onboarding guide
- [ ] Operations runbook
- [ ] Troubleshooting guide

## CI/CD
- [ ] GitHub Actions workflow
- [ ] Automated testing pipeline
- [ ] Docker image scanning
- [ ] Automated deployment
- [ ] Rollback strategy

## Performance
- [ ] Database query optimization
- [ ] N+1 query elimination
- [ ] Implement pagination everywhere
- [ ] Add response caching
- [ ] CDN integration for static assets

## Deployment (Week 10)
- [ ] Kubernetes deployment manifests
- [ ] Helm charts
- [ ] Production environment setup
- [ ] Staging environment setup
- [ ] Blue-green deployment
- [ ] Monitoring and alerting setup

## Known Issues
- [x] Integration-tests module: JMeter API compatibility fixed, needs verification
- [x] Order service was missing from docker-compose.yml - FIXED
- [x] User/Product/Cart services had ID type mismatches (Long vs String) - FIXED
- [x] Missing REST endpoints for testing (users, orders, cart) - FIXED
- [ ] **Integration Tests: 7 out of 37 tests still failing (see Integration Tests section for details)**
  - Cart service validation issues (3 tests)
  - Order authentication flow (2 tests)
  - Saga compensation scenarios (2 tests)
- [ ] Docker build context: Currently requires pre-built JARs
- [ ] Missing infrastructure service tests
- [ ] Auth0 configuration needs actual tenant setup
- [ ] Kafka topics need proper partitioning configuration

## Nice to Have
- [ ] Implement event sourcing for Order Service
- [ ] Add CQRS pattern for read-heavy services
- [ ] Implement distributed tracing correlation
- [ ] Add feature flags
- [ ] Implement A/B testing framework
- [ ] Add real-time notifications (WebSockets)
- [ ] Implement GraphQL subscriptions
- [ ] Add service mesh (Istio/Linkerd)
