# Distributed Tracing Setup Guide

> Back to [README](../README.md).

This document describes the distributed tracing infrastructure for the e-commerce platform.

## Architecture Overview

Our tracing setup uses:
- **Brave/Zipkin** for distributed tracing
- **Micrometer Tracing** for Spring Boot integration
- **MDC (Mapped Diagnostic Context)** for correlation IDs in logs
- **Logstash Encoder** for structured JSON logging

## Components

### 1. Zipkin Server

Zipkin is the central trace collection and visualization system.

**Access**: `http://localhost:9411`

**Docker Compose Configuration**:
```yaml
zipkin:
  image: openzipkin/zipkin:latest
  container_name: zipkin
  ports:
    - "9411:9411"
  environment:
    STORAGE_TYPE: mem
  networks:
    - ecommerce-network
```

### 2. Common Library Tracing Components

All tracing utilities are in the `common-library` module:

```
common-library/src/main/java/com/ecommerce/common/tracing/
├── config/
│   └── TracingConfig.java              # Brave/Zipkin configuration
├── filter/
│   └── CorrelationIdFilter.java        # Correlation ID management
├── util/
│   └── TracingUtil.java                # Tracing helper methods
├── aspect/
│   └── TracedAspect.java               # @Traced annotation handler
└── annotation/
    └── Traced.java                      # Method tracing annotation
```

## Setup Instructions

### Step 1: Start Zipkin

Zipkin is included in the main docker-compose.yml:

```bash
# Start infrastructure including Zipkin
docker-compose up -d zipkin

# Verify Zipkin is running
curl http://localhost:9411/health
```

### Step 2: Configure Your Service

Each microservice needs the following configuration:

#### application.yml

Add the following to your service's `application.yml`:

```yaml
spring:
  application:
    name: your-service-name  # IMPORTANT: Set unique name

management:
  tracing:
    enabled: true
    sampling:
      probability: 1.0  # 100% in dev, 0.1 (10%) in production
  zipkin:
    tracing:
      endpoint: ${ZIPKIN_URL:http://localhost:9411/api/v2/spans}
  metrics:
    tags:
      application: ${spring.application.name}

logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %highlight(%-5level) %cyan(%logger{36}) - [traceId=%X{traceId:-} spanId=%X{spanId:-} correlationId=%X{correlationId:-}] - %msg%n"
```

#### logback-spring.xml

Add a thin `logback-spring.xml` that includes the shared base shipped in
the `common-logging` module (single source of truth at
`common-logging/src/main/resources/logback-includes/logging-base.xml`, placed on
the classpath by depending on `common-logging`):

```xml
<configuration>
    <include resource="logback-includes/logging-base.xml"/>
</configuration>
```

The base emits a human-readable console for local/default and structured JSON
(`service`, `level`, `traceId`, `spanId`, `@timestamp`, `message`, `logger`, `thread`)
under the `docker`, `prod` or `json-logging` profile, ready for Promtail -> Loki ingestion.
Services without a common-library dependency mirror `logging-base.xml` under the same
resource path and add the managed `logstash-logback-encoder` dependency.

#### Docker Environment Variables

In docker-compose.yml, add:

```yaml
your-service:
  environment:
    ZIPKIN_URL: http://zipkin:9411/api/v2/spans
```

### Step 3: Use Tracing in Your Code

#### Option 1: Annotation-Based (Recommended)

```java
@Service
public class OrderService {

    @Traced(value = "create-order", operation = "business-logic")
    public Order createOrder(CreateOrderRequest request) {
        // Automatically traced with custom span
        return order;
    }
}
```

#### Option 2: Manual Tracing

```java
@Service
public class ProductService {

    private final TracingUtil tracingUtil;

    public Product getProduct(String productId) {
        return tracingUtil.traceOperation("get-product", () -> {
            tracingUtil.addTag("product.id", productId);
            return productRepository.findById(productId);
        });
    }
}
```

#### Option 3: Low-Level Span API

```java
public void complexOperation() {
    Span span = tracingUtil.createDatabaseSpan("SELECT", "orders");
    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
        // Your operation
        span.tag("result.count", String.valueOf(count));
    } finally {
        span.finish();
    }
}
```

## Correlation IDs

Correlation IDs are automatically managed by `CorrelationIdFilter`. The filter:

1. Extracts or generates correlation IDs from request headers:
   - `X-Correlation-ID`: Main correlation ID
   - `X-Request-ID`: Request-specific ID
   - `X-User-ID`: User identifier

2. Adds IDs to MDC for logging
3. Propagates IDs to downstream services
4. Returns IDs in response headers

### Using Correlation IDs

```java
@RestController
public class OrderController {

    @GetMapping("/orders/{id}")
    public OrderResponse getOrder(@PathVariable String id, HttpServletRequest request) {
        // Correlation ID is in response headers automatically
        // Also available in MDC
        String correlationId = MDC.get("correlationId");
        log.info("Processing order request");  // Log includes correlationId
        return orderService.getOrder(id);
    }
}
```

## Viewing Traces

### Zipkin UI

1. Open `http://localhost:9411`
2. Click "Find Traces"
3. Search by:
   - Service name (e.g., "order-service")
   - Trace ID
   - Time range
   - Tags (e.g., `http.status_code=500`)

### Understanding the Trace View

```
Trace Timeline:
├── api-gateway (150ms)
│   ├── order-service.create-order (120ms)
│   │   ├── grpc.CartService/getCart (20ms)
│   │   ├── grpc.InventoryService/reserveStock (30ms)
│   │   ├── grpc.PaymentService/createIntent (40ms)
│   │   ├── database.INSERT (15ms)
│   │   └── kafka.produce (10ms)
│   └── response (5ms)
```

Each span shows:
- **Duration**: How long the operation took
- **Tags**: Metadata (e.g., `order.id`, `payment.status`)
- **Logs**: Annotations added during execution
- **Errors**: Exception information if operation failed

## Log Correlation

All logs automatically include trace information:

**Development (Console)**:
```
2025-01-15 10:30:45.123 [http-nio-8080-exec-1] INFO  OrderService - [traceId=abc123 spanId=def456 correlationId=xyz789] - Creating order for user 12345
```

**Production (JSON)**:
```json
{
  "@timestamp": "2025-01-15T10:30:45.123Z",
  "level": "INFO",
  "logger": "com.ecommerce.order.OrderService",
  "message": "Creating order for user 12345",
  "traceId": "abc123",
  "spanId": "def456",
  "correlationId": "xyz789",
  "service": "order-service"
}
```

## Performance Tuning

### Sampling Strategy

Control how many traces are collected:

```yaml
management:
  tracing:
    sampling:
      probability: 0.1  # Sample 10% of traces
```

**Recommendations**:
- **Development**: 1.0 (100%) - trace everything
- **Staging**: 0.3 (30%) - balance visibility and overhead
- **Production (low traffic)**: 0.1 (10%)
- **Production (high traffic)**: 0.01 (1%)

### Reducing Overhead

1. **Tag Size**: Tags are automatically limited to 100 chars
2. **Sensitive Data**: Passwords, tokens, secrets are masked
3. **Async Operations**: Use proper span scope management

## Troubleshooting

### Traces Not Appearing in Zipkin

**Problem**: Traces are not showing up in Zipkin UI

**Solutions**:
1. Verify Zipkin is running: `curl http://localhost:9411/health`
2. Check service logs for Zipkin connection errors
3. Verify configuration: `management.zipkin.tracing.endpoint`
4. Check sampling probability > 0
5. Ensure network connectivity between services and Zipkin

### Missing Correlation IDs

**Problem**: Correlation IDs not appearing in logs

**Solutions**:
1. Verify `CorrelationIdFilter` is registered as a bean
2. Check logback pattern includes `%X{correlationId}`
3. Verify MDC scope decorator is configured
4. Check filter order (should be highest precedence)

### Incomplete Trace Chains

**Problem**: Traces don't show complete service chain

**Solutions**:
1. Ensure all services have tracing enabled
2. Verify gRPC/HTTP clients propagate trace context
3. Check baggage propagation configuration
4. Verify all services can reach Zipkin

### High Memory Usage

**Problem**: Zipkin consuming too much memory

**Solutions**:
1. Switch from in-memory to persistent storage:
   ```yaml
   zipkin:
     environment:
       STORAGE_TYPE: elasticsearch  # or mysql
       ES_HOSTS: http://elasticsearch:9200
   ```
2. Reduce trace retention period
3. Lower sampling probability in services

## Integration Examples

### gRPC Services

Tracing is automatically integrated via `grpc-spring-boot-starter`. For explicit spans:

```java
@GrpcService
public class OrderGrpcServiceImpl extends OrderServiceGrpc.OrderServiceImplBase {

    private final TracingUtil tracingUtil;

    @Override
    public void createOrder(OrderRequest request, StreamObserver<OrderResponse> responseObserver) {
        Span span = tracingUtil.createGrpcSpan("OrderService", "createOrder");
        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
            // Process order
            span.tag("order.userId", request.getUserId());
        } finally {
            span.finish();
        }
    }
}
```

### Kafka Events

```java
@Component
public class OrderEventPublisher {

    private final TracingUtil tracingUtil;
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    public void publishOrderCreated(OrderEvent event) {
        Span span = tracingUtil.createKafkaSpan("produce", "order-events");
        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
            kafkaTemplate.send("order-events", event.getOrderId(), event);
            span.tag("event.type", "OrderCreated");
        } finally {
            span.finish();
        }
    }
}
```

### Database Operations

```java
@Repository
public class OrderRepository {

    private final TracingUtil tracingUtil;

    @Traced(operation = "database")
    public Order save(Order order) {
        // Automatically traced as database operation
        return entityManager.persist(order);
    }

    // Or manually for more control
    public List<Order> findByUserId(String userId) {
        Span span = tracingUtil.createDatabaseSpan("SELECT", "orders");
        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
            span.tag("filter.userId", userId);
            List<Order> orders = // ... query
            span.tag("result.count", String.valueOf(orders.size()));
            return orders;
        } finally {
            span.finish();
        }
    }
}
```

## Best Practices

1. **Name Spans Descriptively**: Use business-meaningful names
   - ✅ `create-order`, `validate-payment`, `reserve-inventory`
   - ❌ `method1`, `doWork`, `execute`

2. **Add Relevant Tags**: Include business context
   ```java
   span.tag("order.id", orderId);
   span.tag("order.total", total.toString());
   span.tag("user.id", userId);
   ```

3. **Don't Over-Trace**: Focus on significant operations
   - ✅ Service calls, DB queries, business logic
   - ❌ Getters, setters, simple utilities

4. **Handle Errors Properly**:
   ```java
   try {
       // operation
       span.tag("status", "success");
   } catch (Exception e) {
       span.tag("error", "true");
       span.tag("error.message", e.getMessage());
       throw e;
   }
   ```

5. **Use Annotations for Simplicity**: Prefer `@Traced` for common cases

6. **Test Tracing**: Verify traces appear correctly in Zipkin during development

## Monitoring Checklist

- [ ] Zipkin is running and accessible
- [ ] All services are configured with Zipkin endpoint
- [ ] Sampling probability is appropriate for environment
- [ ] Logs include trace IDs and correlation IDs
- [ ] Traces show complete service chains
- [ ] Important operations have custom spans
- [ ] Errors are properly tagged in traces
- [ ] Sensitive data is masked in tags

## Additional Resources

- [Zipkin Documentation](https://zipkin.io/pages/instrumenting.html)
- [Micrometer Tracing](https://micrometer.io/docs/tracing)
- [Spring Boot Observability](https://spring.io/blog/2022/10/12/observability-with-spring-boot-3)
- [Common Library Tracing README](../common-library/src/main/java/com/ecommerce/common/tracing/README.md)
