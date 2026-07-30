# Integration Patterns and API Contracts

> Back to [README](../README.md).

## Table of Contents
1. [Overview](#overview)
2. [Service Communication Patterns](#service-communication-patterns)
3. [API Contracts](#api-contracts)
4. [Saga Pattern Implementation](#saga-pattern-implementation)
5. [Event-Driven Architecture](#event-driven-architecture)
6. [Error Handling and Resilience](#error-handling-and-resilience)
7. [Testing Strategies](#testing-strategies)

---

## Overview

This document describes the integration patterns used in the e-commerce microservices platform and defines the API contracts between services.

### Architecture Principles
- **Service Independence**: Each service can be deployed and scaled independently
- **Protocol Diversity**: REST for public APIs, gRPC for internal high-performance calls
- **Event-Driven**: Services communicate via Kafka events for async operations
- **Resilience**: Circuit breakers, timeouts, and retries for fault tolerance
- **Observability**: Distributed tracing and metrics for all service interactions

---

## Service Communication Patterns

### 1. Synchronous Communication

#### REST APIs (Public-Facing)
Used for:
- API Gateway to Frontend
- Admin operations
- Public product catalog
- User profile management

**Example: Product Service REST API**
```http
GET /api/products
POST /api/products
GET /api/products/{id}
PUT /api/products/{id}
DELETE /api/products/{id}
```

#### gRPC (Internal Service-to-Service)
Used for:
- Order Service → Cart Service (fetch cart items)
- Order Service → Inventory Service (reserve stock)
- Order Service → Payment Service (create payment intent)

**Benefits of gRPC:**
- Lower latency (binary protocol)
- Strong typing (Protocol Buffers)
- Bidirectional streaming
- HTTP/2 multiplexing

**Example: Cart Service gRPC**
```protobuf
service CartService {
  rpc GetCart(GetCartRequest) returns (GetCartResponse);
  rpc AddItem(AddItemRequest) returns (AddItemResponse);
  rpc RemoveItem(RemoveItemRequest) returns (RemoveItemResponse);
  rpc ClearCart(ClearCartRequest) returns (ClearCartResponse);
}
```

### 2. Asynchronous Communication

#### Kafka Events
Used for:
- Event sourcing and audit trails
- Service decoupling
- Asynchronous notifications
- Data synchronization

**Event Types:**
```
UserCreatedEvent
ProductCreatedEvent
ProductUpdatedEvent
OrderCreatedEvent
OrderUpdatedEvent
OrderCancelledEvent
PaymentCompletedEvent
PaymentFailedEvent
InventoryReservedEvent
InventoryReleasedEvent
```

**Event Schema Example:**
```json
{
  "eventId": "uuid",
  "eventType": "OrderCreatedEvent",
  "timestamp": "2025-01-15T10:30:00Z",
  "aggregateId": "order-123",
  "aggregateType": "Order",
  "version": 1,
  "payload": {
    "orderId": "order-123",
    "userId": "user-456",
    "orderNumber": "ORD-2025-00001",
    "total": 109.97,
    "status": "PENDING"
  }
}
```

---

## API Contracts

### User Service

#### Create User
```http
POST /api/users
Content-Type: application/json

{
  "auth0Id": "auth0|123456",
  "email": "user@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "phoneNumber": "+1234567890"
}

Response: 201 Created
{
  "id": "user-uuid",
  "auth0Id": "auth0|123456",
  "email": "user@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "phoneNumber": "+1234567890",
  "createdAt": "2025-01-15T10:30:00Z"
}
```

#### Get User Profile
```http
GET /api/users/{userId}
Authorization: Bearer {token}

Response: 200 OK
{
  "id": "user-uuid",
  "email": "user@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "addresses": [
    {
      "id": "addr-uuid",
      "street": "123 Main St",
      "city": "New York",
      "state": "NY",
      "postalCode": "10001",
      "country": "USA",
      "isDefault": true
    }
  ]
}
```

### Product Service

#### List Products
```http
GET /api/products?page=0&size=20&category=Electronics&minPrice=0&maxPrice=1000
Authorization: Bearer {token}

Response: 200 OK
{
  "content": [
    {
      "id": "prod-uuid",
      "sku": "PROD-001",
      "name": "Product Name",
      "description": "Product description",
      "category": "Electronics",
      "price": 99.99,
      "currency": "USD",
      "stockQuantity": 50,
      "active": true,
      "images": ["url1", "url2"]
    }
  ],
  "totalElements": 100,
  "totalPages": 5,
  "number": 0,
  "size": 20
}
```

#### Create Product (Admin)
```http
POST /api/products
Authorization: Bearer {admin-token}
Content-Type: application/json

{
  "sku": "PROD-001",
  "name": "Product Name",
  "description": "Product description",
  "category": "Electronics",
  "price": 99.99,
  "currency": "USD",
  "stockQuantity": 50,
  "active": true
}

Response: 201 Created
{
  "id": "prod-uuid",
  "sku": "PROD-001",
  ...
}
```

### Cart Service

#### Add Item to Cart
```http
POST /api/cart/{userId}/items
Authorization: Bearer {token}
Content-Type: application/json

{
  "productId": "prod-uuid",
  "productName": "Product Name",
  "price": 99.99,
  "quantity": 2
}

Response: 200 OK
{
  "id": "cart-uuid",
  "userId": "user-uuid",
  "items": [
    {
      "productId": "prod-uuid",
      "productName": "Product Name",
      "price": 99.99,
      "quantity": 2,
      "subtotal": 199.98
    }
  ],
  "subtotal": 199.98,
  "updatedAt": "2025-01-15T10:30:00Z"
}
```

#### Get Cart (gRPC)
```protobuf
message GetCartRequest {
  string user_id = 1;
}

message GetCartResponse {
  bool success = 1;
  string message = 2;
  Cart cart = 3;
}

message Cart {
  string id = 1;
  string user_id = 2;
  repeated CartItem items = 3;
  double subtotal = 4;
}
```

### Order Service

#### Create Order
```http
POST /api/orders
Authorization: Bearer {token}
Content-Type: application/json

{
  "userId": "user-uuid",
  "shippingAddress": {
    "street": "123 Main St",
    "city": "New York",
    "state": "NY",
    "postalCode": "10001",
    "country": "USA"
  },
  "promotionCode": "SAVE10"
}

Response: 201 Created
{
  "id": "order-uuid",
  "orderNumber": "ORD-2025-00001",
  "userId": "user-uuid",
  "items": [...],
  "subtotal": 199.98,
  "tax": 16.00,
  "shippingCost": 9.99,
  "discount": 20.00,
  "total": 205.97,
  "status": "PENDING",
  "shippingAddress": {...},
  "paymentIntentId": "pi_123456",
  "createdAt": "2025-01-15T10:30:00Z"
}
```

#### Update Order Status (gRPC)
```protobuf
message UpdateOrderStatusRequest {
  string order_id = 1;
  OrderStatus status = 2;
}

enum OrderStatus {
  PENDING = 0;
  CONFIRMED = 1;
  PROCESSING = 2;
  SHIPPED = 3;
  DELIVERED = 4;
  CANCELLED = 5;
  REFUNDED = 6;
}
```

### Inventory Service

#### Reserve Stock (gRPC)
```protobuf
message ReserveStockRequest {
  string product_id = 1;
  string order_id = 2;
  int32 quantity = 3;
}

message ReserveStockResponse {
  bool success = 1;
  string message = 2;
  string reservation_id = 3;
}
```

#### Check Availability (gRPC)
```protobuf
message CheckAvailabilityRequest {
  string product_id = 1;
  int32 quantity = 2;
}

message CheckAvailabilityResponse {
  bool available = 1;
  int32 available_quantity = 2;
}
```

### Payment Service

#### Create Payment Intent (gRPC)
```protobuf
message CreatePaymentIntentRequest {
  string order_id = 1;
  double amount = 2;
  string currency = 3;
}

message CreatePaymentIntentResponse {
  bool success = 1;
  string message = 2;
  string payment_intent_id = 3;
  string client_secret = 4;
}
```

---

## Saga Pattern Implementation

### Order Creation Saga

The order creation process uses the Saga pattern to ensure consistency across multiple services.

#### Saga Steps (Forward Flow)

1. **Fetch Cart**
   - Service: Cart Service
   - Action: Get cart items for user
   - Compensation: N/A (read-only)

2. **Reserve Inventory**
   - Service: Inventory Service
   - Action: Create inventory reservation
   - Compensation: Release reservation

3. **Create Payment Intent**
   - Service: Payment Service
   - Action: Create payment intent
   - Compensation: Cancel payment intent

4. **Create Order**
   - Service: Order Service
   - Action: Save order to database
   - Compensation: Mark order as failed

5. **Clear Cart**
   - Service: Cart Service
   - Action: Remove items from cart
   - Compensation: Restore cart items

6. **Publish Events**
   - Service: Order Service
   - Action: Publish OrderCreatedEvent
   - Compensation: Publish OrderFailedEvent

#### Saga Compensation (Rollback Flow)

When any step fails, compensating transactions are executed in reverse order:

```java
@Service
public class OrderCreationSaga {

    public Order createOrder(String userId, List<OrderItem> items, Address address) {
        SagaContext context = new SagaContext();

        try {
            // Step 1: Fetch cart
            Cart cart = cartService.getCart(userId);
            context.setCart(cart);

            // Step 2: Reserve inventory
            List<String> reservationIds = inventoryService.reserveStock(items);
            context.setReservationIds(reservationIds);

            // Step 3: Create payment intent
            PaymentIntent paymentIntent = paymentService.createPaymentIntent(order);
            context.setPaymentIntent(paymentIntent);

            // Step 4: Create order
            Order order = orderRepository.save(order);
            context.setOrder(order);

            // Step 5: Clear cart
            cartService.clearCart(userId);

            // Step 6: Publish events
            eventPublisher.publishOrderCreatedEvent(order);

            return order;

        } catch (Exception e) {
            // Execute compensation
            compensate(context, e);
            throw new OrderCreationException("Order creation failed", e);
        }
    }

    private void compensate(SagaContext context, Exception cause) {
        log.error("Saga failed, executing compensation: {}", cause.getMessage());

        // Compensate in reverse order
        if (context.getPaymentIntent() != null) {
            paymentService.cancelPaymentIntent(context.getPaymentIntent().getId());
        }

        if (context.getReservationIds() != null) {
            inventoryService.releaseReservations(context.getReservationIds());
        }

        if (context.getOrder() != null) {
            orderRepository.updateStatus(context.getOrder().getId(), OrderStatus.FAILED);
        }

        // Publish failure event
        eventPublisher.publishOrderFailedEvent(context.getOrder(), cause);
    }
}
```

#### Saga State Machine

```
[PENDING] → Fetch Cart → [CART_FETCHED]
           ↓
           Reserve Inventory → [INVENTORY_RESERVED]
           ↓
           Create Payment → [PAYMENT_CREATED]
           ↓
           Save Order → [ORDER_CREATED]
           ↓
           Clear Cart → [CART_CLEARED]
           ↓
           Publish Events → [COMPLETED]

           ↓ (on error at any step)
           [COMPENSATING] → Execute Rollbacks → [FAILED]
```

---

## Event-Driven Architecture

### Event Publishing

Services publish events to Kafka topics when domain events occur.

#### Event Publisher Pattern

```java
@Service
public class OrderEventPublisher {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String ORDER_EVENTS_TOPIC = "order-events";

    public void publishOrderCreatedEvent(Order order) {
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("OrderCreatedEvent")
                .timestamp(Instant.now())
                .aggregateId(order.getId())
                .aggregateType("Order")
                .version(1)
                .payload(order)
                .build();

        try {
            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(ORDER_EVENTS_TOPIC, order.getId(), eventJson);
            log.info("Published OrderCreatedEvent for order: {}", order.getId());
        } catch (Exception e) {
            log.error("Failed to publish OrderCreatedEvent", e);
            // Handle failure (retry, dead letter queue, etc.)
        }
    }
}
```

### Event Consumption

Services subscribe to relevant event topics and react accordingly.

#### Event Consumer Pattern

```java
@Service
public class NotificationEventConsumer {

    @KafkaListener(topics = "order-events", groupId = "notification-service")
    public void handleOrderEvent(String eventJson) {
        try {
            OrderEvent event = objectMapper.readValue(eventJson, OrderEvent.class);

            switch (event.getEventType()) {
                case "OrderCreatedEvent":
                    sendOrderConfirmationEmail(event);
                    break;
                case "OrderShippedEvent":
                    sendShippingNotification(event);
                    break;
                case "OrderDeliveredEvent":
                    sendDeliveryConfirmation(event);
                    break;
            }

        } catch (Exception e) {
            log.error("Error processing order event", e);
            // Event will be retried by Kafka consumer
        }
    }
}
```

### Event Topics

```
user-events          → User lifecycle events
product-events       → Product catalog changes
cart-events          → Cart modifications
order-events         → Order lifecycle
payment-events       → Payment transactions
inventory-events     → Stock changes
notification-events  → Notification triggers
```

---

## Error Handling and Resilience

### Circuit Breaker Pattern

Services use Resilience4j circuit breakers to prevent cascading failures.

```java
@Service
public class OrderService {

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "reserveStockFallback")
    @Retry(name = "inventoryService")
    @TimeLimiter(name = "inventoryService")
    public List<String> reserveStock(List<OrderItem> items) {
        return inventoryGrpcClient.reserveStock(items);
    }

    private List<String> reserveStockFallback(List<OrderItem> items, Exception e) {
        log.error("Inventory service unavailable, using fallback", e);
        throw new ServiceUnavailableException("Inventory service temporarily unavailable");
    }
}
```

### Retry Strategy

```yaml
resilience4j:
  retry:
    instances:
      inventoryService:
        max-attempts: 3
        wait-duration: 500ms
        exponential-backoff-multiplier: 2
        retry-exceptions:
          - java.net.ConnectException
          - java.net.SocketTimeoutException
```

### Circuit Breaker Configuration

```yaml
resilience4j:
  circuitbreaker:
    instances:
      inventoryService:
        failure-rate-threshold: 50
        slow-call-rate-threshold: 50
        slow-call-duration-threshold: 2s
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 5
        sliding-window-size: 10
```

---

## Testing Strategies

### 1. Unit Tests
- Test individual service logic
- Mock external dependencies
- Fast execution

### 2. Integration Tests
- Test service interactions
- Use Testcontainers for infrastructure
- Verify API contracts

### 3. End-to-End Tests
- Test complete user flows
- All services running
- Verify business scenarios

### 4. Contract Tests
- Verify API contracts between services
- Consumer-driven contract testing
- Prevent breaking changes

### 5. Performance Tests
- Load testing with JMeter
- Virtual threads performance
- Latency and throughput metrics

### 6. Chaos Testing
- Simulate service failures
- Test circuit breakers
- Verify saga compensation

---

## Testing Commands

```bash
# Run all integration tests
mvn clean verify -pl integration-tests

# Run specific test class
mvn test -Dtest=OrderFlowIntegrationTest

# Run with coverage
mvn clean verify jacoco:report

# Run load tests
mvn test -Dtest=LoadTestRunner

# Start services for manual testing
docker-compose up -d

# Check service health
./scripts/check-health.sh
```

---

## Performance Targets

| Metric | Target | Current |
|--------|--------|---------|
| API Response Time (p95) | < 200ms | ✓ |
| gRPC Call Latency (p95) | < 50ms | ✓ |
| Throughput | 100+ orders/min | ✓ |
| Concurrent Users | 1000+ | ✓ |
| Error Rate | < 1% | ✓ |
| Availability | 99.9% | - |

---

## Troubleshooting

### Common Issues

1. **Service Connection Refused**
   - Verify service is running: `docker-compose ps`
   - Check health endpoint: `curl http://localhost:8080/actuator/health`

2. **gRPC Connection Failed**
   - Verify gRPC port is open
   - Check firewall settings
   - Review gRPC client configuration

3. **Kafka Events Not Received**
   - Check Kafka broker is running
   - Verify topic exists
   - Review consumer group status

4. **High Latency**
   - Check database connection pool
   - Review slow query logs
   - Verify circuit breaker states
   - Check for resource contention

---

## Next Steps

1. Implement additional services (Notification, Search, Media, Promotion)
2. Add API Gateway rate limiting
3. Implement distributed caching with Redis
4. Add comprehensive monitoring dashboards
5. Implement API versioning
6. Add GraphQL API layer
7. Implement batch processing jobs
8. Add advanced search capabilities

---

For more information, see:
- [Architecture Documentation](ARCHITECTURE.md)
- [API Reference](API_DOCUMENTATION.md)
- [Deployment Guide](DEPLOYMENT.md)
