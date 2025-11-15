# Cart Service - TODO & Enhancements

## High Priority Enhancements

### 1. Redis Caching Implementation
**Status**: Pending
**Effort**: Medium
**Impact**: High

Implement Redis caching to improve performance and reduce database load.

**Tasks**:
- [ ] Add `@Cacheable` annotations to cart retrieval methods
- [ ] Implement cache eviction on cart updates
- [ ] Configure Redis serialization for Cart entities
- [ ] Add cache warming on service startup
- [ ] Implement distributed locking for cart operations
- [ ] Add cache metrics and monitoring

**Files to modify**:
- `CartService.java` - Add caching annotations
- `application.yml` - Configure cache settings
- Add new `CacheConfig.java` for Redis configuration

---

### 2. Cart Expiration & Cleanup Job
**Status**: Pending
**Effort**: Low
**Impact**: Medium

Implement scheduled job to clean up expired and abandoned carts.

**Tasks**:
- [ ] Create `CartCleanupScheduler` with @Scheduled annotation
- [ ] Implement logic to find and delete expired carts
- [ ] Add configuration for cleanup schedule (cron expression)
- [ ] Log cleanup metrics (number of carts deleted)
- [ ] Send Kafka events for abandoned carts (for marketing)
- [ ] Add admin endpoint to trigger manual cleanup

**New files**:
- `scheduler/CartCleanupScheduler.java`
- `service/CartCleanupService.java`

---

### 3. Anonymous Cart Support & Merge
**Status**: Pending
**Effort**: High
**Impact**: High

Support carts for anonymous users and merge them when user logs in.

**Tasks**:
- [ ] Use session ID or UUID for anonymous users
- [ ] Modify Cart entity to support nullable userId
- [ ] Implement cart merge logic (combine anonymous + user cart)
- [ ] Handle duplicate products during merge
- [ ] Add merge endpoint triggered by authentication event
- [ ] Add Kafka listener for user login events
- [ ] Update security config to allow anonymous access to cart endpoints

**Files to modify**:
- `Cart.java` - Allow nullable userId
- `CartService.java` - Add merge logic
- `CartController.java` - Support anonymous access
- `SecurityConfig.java` - Update security rules

---

### 4. Price Validation on Checkout
**Status**: Pending
**Effort**: Medium
**Impact**: High

Re-validate product prices when converting cart to order to handle price changes.

**Tasks**:
- [ ] Create `CartValidationService`
- [ ] Implement price comparison logic (snapshot vs current)
- [ ] Add stock availability re-check
- [ ] Create validation response DTO with warnings
- [ ] Add validation endpoint (pre-checkout validation)
- [ ] Send notification if prices changed
- [ ] Add option to update prices or cancel checkout

**New files**:
- `service/CartValidationService.java`
- `dto/CartValidationResponse.java`
- `dto/CartValidationWarning.java`

---

## Medium Priority Enhancements

### 5. Integration Tests
**Status**: Pending
**Effort**: Medium
**Impact**: High

Add comprehensive integration tests using TestContainers.

**Tasks**:
- [ ] Create `CartServiceIntegrationTest` with TestContainers
- [ ] Test all cart operations (CRUD)
- [ ] Test cart expiration logic
- [ ] Test Feign client integration with WireMock
- [ ] Test concurrent cart operations
- [ ] Add performance tests

**New files**:
- `test/java/com/ecommerce/cartservice/CartServiceIntegrationTest.java`
- `test/java/com/ecommerce/cartservice/CartControllerIntegrationTest.java`

---

### 6. Kafka Event Publishing
**Status**: Pending
**Effort**: Low
**Impact**: Medium

Publish cart events to Kafka for analytics and notifications.

**Tasks**:
- [ ] Create event DTOs (CartCreated, ItemAdded, CartAbandoned, etc.)
- [ ] Create `CartEventPublisher` service
- [ ] Publish events on cart operations
- [ ] Add event versioning
- [ ] Configure Kafka topics
- [ ] Add event publishing metrics

**New files**:
- `event/CartEvent.java`
- `event/CartEventType.java`
- `service/CartEventPublisher.java`

**Topics**:
- `cart.created`
- `cart.item.added`
- `cart.item.removed`
- `cart.checked-out`
- `cart.abandoned`

---

### 7. Coupon/Promotion Integration
**Status**: Pending
**Effort**: High
**Impact**: High

Support applying promotional codes and discounts to cart.

**Tasks**:
- [ ] Add coupon fields to Cart entity (couponCode, discount)
- [ ] Create Feign client for Promotion Service
- [ ] Add apply/remove coupon endpoints
- [ ] Recalculate totals with discount
- [ ] Validate coupon eligibility (min purchase, product restrictions)
- [ ] Show discount breakdown in cart response

**Files to modify**:
- `Cart.java` - Add coupon fields
- `CartService.java` - Add coupon logic
- `CartController.java` - Add coupon endpoints

---

### 8. Saved for Later Feature
**Status**: Pending
**Effort**: Medium
**Impact**: Medium

Allow users to save items for later purchase.

**Tasks**:
- [ ] Add `savedForLater` boolean to CartItem
- [ ] Add move-to-saved and move-to-cart endpoints
- [ ] Update cart totals to exclude saved items
- [ ] Add separate list endpoint for saved items
- [ ] Send reminders for saved items

**Files to modify**:
- `CartItem.java` - Add savedForLater field
- `CartService.java` - Add saved items logic
- `CartController.java` - Add saved items endpoints

---

## Low Priority Enhancements

### 9. Cart Recommendations
**Status**: Pending
**Effort**: High
**Impact**: Medium

Suggest related products based on cart contents.

**Tasks**:
- [ ] Integrate with recommendation engine
- [ ] Add recommendations endpoint
- [ ] Show "frequently bought together" items
- [ ] Show "customers also bought" items

---

### 10. Stock Reservation
**Status**: Pending
**Effort**: High
**Impact**: High

Reserve stock when items are added to cart.

**Tasks**:
- [ ] Integrate with Inventory Service
- [ ] Reserve stock on add-to-cart
- [ ] Release reservation on remove or expiration
- [ ] Handle reservation timeout
- [ ] Add reservation status to cart response

---

### 11. Cart Sharing
**Status**: Pending
**Effort**: Medium
**Impact**: Low

Allow users to share their cart via a link.

**Tasks**:
- [ ] Generate shareable cart tokens
- [ ] Add public cart view endpoint (no auth)
- [ ] Allow cloning shared cart to user's cart
- [ ] Set expiration for shared links

---

### 12. Multi-Currency Support
**Status**: Pending
**Effort**: High
**Impact**: Medium

Support multiple currencies and automatic conversion.

**Tasks**:
- [ ] Add currency field to Cart
- [ ] Integrate with currency conversion service
- [ ] Store prices in base currency
- [ ] Convert for display based on user preference
- [ ] Handle currency change in existing cart

---

## Performance Optimizations

### 13. Database Optimizations
- [ ] Add database connection pooling tuning
- [ ] Optimize cart retrieval queries (N+1 problem)
- [ ] Add database indexes for common queries
- [ ] Implement read replicas for cart reads
- [ ] Add pagination for large carts

### 14. API Rate Limiting
- [ ] Implement rate limiting per user
- [ ] Add throttling for add-to-cart operations
- [ ] Configure Redis-based rate limiter

### 15. Batch Operations
- [ ] Add bulk add-to-cart endpoint
- [ ] Add bulk remove-from-cart endpoint
- [ ] Optimize database operations with batching

---

## Monitoring & Observability

### 16. Advanced Metrics
- [ ] Track cart conversion rate
- [ ] Track average cart value
- [ ] Track cart abandonment rate
- [ ] Track most added products
- [ ] Add custom Prometheus metrics

### 17. Distributed Tracing
- [ ] Add correlation IDs
- [ ] Integrate with Zipkin/Jaeger
- [ ] Add custom trace attributes

---

## Security Enhancements

### 18. Additional Security
- [ ] Add CSRF protection
- [ ] Implement request signing
- [ ] Add audit logging for cart operations
- [ ] Implement data encryption at rest
- [ ] Add PII data masking in logs

---

## Documentation

### 19. Additional Documentation
- [ ] Add API usage examples
- [ ] Create architecture diagrams
- [ ] Document error codes and handling
- [ ] Add troubleshooting guide
- [ ] Create runbook for operations

---

## Notes

- Prioritize enhancements based on business requirements
- Ensure backward compatibility when making changes
- Write tests for all new features
- Update documentation with each enhancement
- Consider feature flags for gradual rollout
