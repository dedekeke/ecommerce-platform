# Distributed Tracing Documentation

This package provides comprehensive distributed tracing capabilities for all microservices in the e-commerce platform.

## Features

- **Automatic Trace Propagation**: Traces are automatically propagated across service boundaries using Brave/Zipkin
- **Correlation IDs**: Every request gets a correlation ID that flows through all services
- **MDC Integration**: Trace IDs, span IDs, and correlation IDs are automatically added to logs
- **Structured Logging**: JSON-formatted logs with tracing metadata
- **Custom Spans**: Create custom spans for business operations
- **Annotation-based Tracing**: Use `@Traced` annotation for automatic span creation

## Configuration

### 1. Application Properties

Add the following to your `application.yml` or `application.properties`:

```yaml
# Tracing Configuration
management:
  tracing:
    enabled: true
    sampling:
      probability: 1.0  # Sample 100% of traces in dev (reduce in production)
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans

spring:
  application:
    name: your-service-name  # IMPORTANT: Set unique name for each service
```

### 2. Logback Configuration

Add a thin `src/main/resources/logback-spring.xml` that includes the shared base
shipped in common-library (`logback-includes/logging-base.xml`):

```xml
<configuration>
    <include resource="logback-includes/logging-base.xml"/>
</configuration>
```

The base provides a human-readable console for local/default profiles and structured
JSON (LogstashEncoder, with `service`/`traceId`/`spanId` fields for Loki) under the
`prod` or `json-logging` profile. Services that do not depend on common-library mirror
`logging-base.xml` under the same resource path and declare the
`net.logstash.logback:logstash-logback-encoder` dependency (managed in the parent pom).

## Usage

### 1. Automatic Tracing with @Traced Annotation

```java
@Service
public class OrderService {

    @Traced(value = "create-order", operation = "business-logic")
    public Order createOrder(CreateOrderRequest request) {
        // Your business logic here
        return order;
    }

    @Traced(operation = "database", includeParameters = true)
    public Order findOrderById(String orderId) {
        // Database query
        return orderRepository.findById(orderId);
    }
}
```

### 2. Manual Span Creation

```java
@Service
public class ProductService {

    private final TracingUtil tracingUtil;

    public ProductService(TracingUtil tracingUtil) {
        this.tracingUtil = tracingUtil;
    }

    public Product getProduct(String productId) {
        return tracingUtil.traceOperation("get-product", () -> {
            // Your logic here
            tracingUtil.addTag("product.id", productId);
            tracingUtil.addAnnotation("Fetching product from database");
            return productRepository.findById(productId);
        });
    }
}
```

### 3. Database Operations

```java
public void saveProduct(Product product) {
    Span span = tracingUtil.createDatabaseSpan("INSERT", "products");
    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
        productRepository.save(product);
        span.tag("status", "success");
    } catch (Exception e) {
        span.tag("status", "error");
        span.tag("error", e.getMessage());
        throw e;
    } finally {
        span.finish();
    }
}
```

### 4. gRPC Calls

```java
public OrderResponse createOrder(OrderRequest request) {
    Span span = tracingUtil.createGrpcSpan("OrderService", "createOrder");
    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
        OrderResponse response = orderServiceStub.createOrder(request);
        span.tag("order.id", response.getOrderId());
        return response;
    } finally {
        span.finish();
    }
}
```

### 5. Kafka Event Publishing

```java
public void publishOrderEvent(OrderCreatedEvent event) {
    Span span = tracingUtil.createKafkaSpan("produce", "order-events");
    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
        kafkaTemplate.send("order-events", event);
        span.tag("event.type", "OrderCreated");
    } finally {
        span.finish();
    }
}
```

### 6. External API Calls

```java
public PaymentResponse processPayment(PaymentRequest request) {
    Span span = tracingUtil.createExternalCallSpan("PaymentGateway", "/api/v1/payments");
    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
        PaymentResponse response = restTemplate.postForObject(
            "https://payment-gateway.com/api/v1/payments",
            request,
            PaymentResponse.class
        );
        span.tag("payment.status", response.getStatus());
        return response;
    } finally {
        span.finish();
    }
}
```

### 7. Accessing Correlation IDs

```java
@RestController
public class OrderController {

    private final TracingUtil tracingUtil;

    @GetMapping("/orders/{id}")
    public OrderResponse getOrder(@PathVariable String id) {
        String traceId = tracingUtil.getCurrentTraceId();
        String spanId = tracingUtil.getCurrentSpanId();

        log.info("Processing order request - traceId: {}, spanId: {}", traceId, spanId);

        // Correlation ID is automatically in MDC
        // Access via: MDC.get("correlationId")

        return orderService.getOrder(id);
    }
}
```

## Correlation ID Headers

The following headers are automatically handled:

- `X-Correlation-ID`: Main correlation ID for the request chain
- `X-Request-ID`: Unique ID for this specific request
- `X-User-ID`: User identifier (if authenticated)

These headers are:
1. Automatically extracted from incoming requests
2. Generated if not present
3. Propagated to downstream services
4. Added to response headers
5. Included in all log statements via MDC

## Log Format

### Development (Console)

```
2025-01-15 10:30:45.123 [http-nio-8080-exec-1] INFO  c.e.order.OrderService - [traceId=abc123 spanId=def456 correlationId=xyz789] - Creating order for user 12345
```

### Production (JSON)

```json
{
  "timestamp": "2025-01-15T10:30:45.123Z",
  "level": "INFO",
  "thread": "http-nio-8080-exec-1",
  "logger": "com.ecommerce.order.OrderService",
  "message": "Creating order for user 12345",
  "application": "order-service",
  "traceId": "abc123",
  "spanId": "def456",
  "correlationId": "xyz789",
  "requestId": "req-001",
  "userId": "12345"
}
```

## Viewing Traces in Zipkin

1. Access Zipkin UI: `http://localhost:9411`
2. Search by:
   - Service name
   - Trace ID
   - Time range
   - Duration
   - Tags (e.g., `http.status_code=500`)

## Best Practices

1. **Use Meaningful Span Names**: Name spans after the business operation, not the technical implementation
   - Good: `create-order`, `calculate-shipping-cost`, `validate-payment`
   - Bad: `method1`, `doSomething`, `process`

2. **Add Relevant Tags**: Include business-relevant information as tags
   ```java
   span.tag("order.id", orderId);
   span.tag("order.amount", amount.toString());
   span.tag("payment.method", paymentMethod);
   ```

3. **Don't Over-trace**: Only create spans for significant operations
   - ✅ Database queries, external API calls, business logic
   - ❌ Simple getters, utility methods, constructors

4. **Handle Errors**: Always tag errors for easy troubleshooting
   ```java
   catch (Exception e) {
       span.tag("error", "true");
       span.tag("error.message", e.getMessage());
       throw e;
   }
   ```

5. **Use Annotations for Milestones**: Mark important events within a span
   ```java
   span.annotate("Payment validation started");
   // ... validation logic ...
   span.annotate("Payment validation completed");
   ```

6. **Adjust Sampling in Production**: Set sampling probability based on traffic
   ```yaml
   management:
     tracing:
       sampling:
         probability: 0.1  # Sample 10% of traces in production
   ```

## Troubleshooting

### Traces Not Appearing in Zipkin

1. Verify Zipkin is running: `curl http://localhost:9411/health`
2. Check configuration: `management.zipkin.tracing.endpoint` is correct
3. Check logs for tracing errors
4. Verify sampling probability > 0

### Missing Correlation IDs in Logs

1. Ensure `CorrelationIdFilter` is being applied (check it's a `@Component`)
2. Verify MDC is being populated (check log pattern includes `%X{correlationId}`)
3. Check filter order (should be `Ordered.HIGHEST_PRECEDENCE`)

### Spans Not Propagating Across Services

1. Verify all services have tracing enabled
2. Check gRPC/HTTP clients are using proper interceptors
3. Verify baggage propagation configuration is correct
4. Check network connectivity between services and Zipkin

## Performance Considerations

1. **Sampling**: In production, sample traces instead of capturing 100%
2. **Span Tags**: Limit the size of tag values (automatically sanitized to 100 chars)
3. **Sensitive Data**: Tracing automatically masks `password`, `token`, `secret` in tags
4. **Async Operations**: Use `tracer.withSpanInScope()` in async contexts

## Integration with Other Services

### gRPC

The tracing is automatically integrated with gRPC via `grpc-spring-boot-starter`. No additional configuration needed.

### Kafka

Tracing headers are automatically propagated through Kafka messages. Use `createKafkaSpan()` for explicit span creation.

### REST Template / WebClient

Add tracing interceptor to your RestTemplate configuration:

```java
@Bean
public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder
        .interceptors(new TracingClientHttpRequestInterceptor())
        .build();
}
```

## Example: Complete Order Flow Trace

```
Trace: create-order (Order Service)
├── grpc.CartService/getCart (Cart Service)
│   └── database.SELECT (carts collection)
├── grpc.InventoryService/reserveStock (Inventory Service)
│   ├── database.SELECT (inventory table)
│   └── database.UPDATE (inventory table)
├── grpc.PaymentService/createPaymentIntent (Payment Service)
│   ├── database.INSERT (payments table)
│   └── external.PaymentGateway (External API)
├── database.INSERT (orders table)
└── kafka.produce (order-events topic)
```

This trace shows the complete flow of creating an order, with visibility into every service call and database operation.
