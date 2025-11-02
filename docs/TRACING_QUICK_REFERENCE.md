# Distributed Tracing Quick Reference

## 🔧 Setup Checklist

- [ ] Service has unique `spring.application.name` in application.yml
- [ ] Zipkin endpoint configured: `management.zipkin.tracing.endpoint`
- [ ] Sampling configured: `management.tracing.sampling.probability`
- [ ] Logback configuration copied from template
- [ ] Common-library dependency added

## 📝 Configuration Template

```yaml
spring:
  application:
    name: your-service-name

management:
  tracing:
    enabled: true
    sampling:
      probability: 1.0  # Dev: 1.0, Prod: 0.1
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
```

## 🎯 Creating Spans (3 Methods)

### Method 1: Annotation (Simplest)
```java
@Traced(value = "create-order", operation = "business-logic")
public Order createOrder(CreateOrderRequest request) {
    // Automatically traced
    return order;
}
```

### Method 2: Utility Method
```java
return tracingUtil.traceOperation("get-product", () -> {
    tracingUtil.addTag("product.id", productId);
    return repository.findById(productId);
});
```

### Method 3: Manual Span
```java
Span span = tracingUtil.createDatabaseSpan("SELECT", "orders");
try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
    List<Order> orders = repository.findAll();
    span.tag("result.count", String.valueOf(orders.size()));
    return orders;
} finally {
    span.finish();
}
```

## 🏷️ Common Span Types

```java
// Database operations
Span span = tracingUtil.createDatabaseSpan("INSERT", "orders");

// gRPC calls
Span span = tracingUtil.createGrpcSpan("OrderService", "createOrder");

// Kafka operations
Span span = tracingUtil.createKafkaSpan("produce", "order-events");

// External APIs
Span span = tracingUtil.createExternalCallSpan("PaymentGateway", "/api/payments");
```

## 🔍 Searching in Zipkin

| Search For | Query |
|------------|-------|
| All errors | `error=true` |
| Slow requests | Min Duration: `1000ms` |
| Specific user | `userId=12345` |
| Correlation ID | `correlationId=abc-123` |
| Service | Use dropdown |
| HTTP errors | `http.status_code=500` |

## 📊 Reading Traces

```
┌─ api-gateway (150ms total) ─────────────────┐
│  ├─ order-service.create-order (120ms) ──┐  │
│  │  ├─ grpc.CartService/getCart (20ms)   │  │
│  │  ├─ database.INSERT (15ms)            │  │
│  │  └─ kafka.produce (10ms)              │  │
│  └───────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
```

**Colors:**
- 🔵 Blue = Service boundary
- 🟢 Green = Success
- 🔴 Red = Error
- ⚪ Gray = Cache hit

## 🏷️ Essential Tags

```java
// Business context
span.tag("order.id", orderId);
span.tag("user.id", userId);
span.tag("order.total", total.toString());

// Technical context
span.tag("db.operation", "INSERT");
span.tag("db.table", "orders");
span.tag("http.method", "POST");
span.tag("http.status_code", "200");

// Error handling
span.tag("error", "true");
span.tag("error.message", e.getMessage());
```

## 🔗 Correlation Headers

**Automatic Headers:**
- `X-Correlation-ID`: Main correlation ID
- `X-Request-ID`: Request-specific ID
- `X-User-ID`: User identifier

**Usage:**
```bash
curl -H "X-Correlation-ID: my-custom-id" \
     http://localhost:8080/api/orders
```

## 📝 Log Format

**Development Console:**
```
2025-01-15 10:30:45.123 [thread] INFO OrderService -
[traceId=abc123 spanId=def456 correlationId=xyz789] - Creating order
```

**Production JSON:**
```json
{
  "timestamp": "2025-01-15T10:30:45.123Z",
  "level": "INFO",
  "logger": "OrderService",
  "message": "Creating order",
  "traceId": "abc123",
  "spanId": "def456",
  "correlationId": "xyz789"
}
```

## 🔧 Troubleshooting

| Problem | Solution |
|---------|----------|
| No traces in Zipkin | Check: Zipkin running? Service config? Sampling > 0? |
| Missing correlationId | Check: CorrelationIdFilter registered? MDC in logback? |
| Incomplete traces | Check: All services configured? Network connectivity? |
| Slow Zipkin | Switch to persistent storage (Elasticsearch) |

## 🎯 Best Practices

✅ **DO:**
- Use descriptive span names
- Add business tags (order.id, user.id)
- Tag all errors
- Use @Traced for common cases
- Review traces regularly

❌ **DON'T:**
- Trace simple getters/setters
- Include sensitive data in tags
- Forget spring.application.name
- Set sampling to 0
- Ignore slow requests

## 📊 Performance Impact

| Sampling | Overhead | Use Case |
|----------|----------|----------|
| 100% | 1-2% | Development |
| 30% | ~0.5% | Staging |
| 10% | <0.5% | Production (low traffic) |
| 1% | Negligible | Production (high traffic) |

## 🔗 Quick Links

- **Zipkin UI**: http://localhost:9411
- **Dependencies**: http://localhost:9411/zipkin/dependency
- **API**: http://localhost:9411/api/v2

## 📚 Common Patterns

### Pattern 1: Service-to-Service Call
```java
@Traced(value = "create-order")
public Order createOrder(CreateOrderRequest request) {
    // Trace propagates automatically
    Cart cart = cartServiceClient.getCart(request.getUserId());
    // ... rest of logic
}
```

### Pattern 2: Async Operations
```java
CompletableFuture.supplyAsync(() -> {
    return tracingUtil.traceOperation("async-task", () -> {
        // Your async work here
    });
}, executor);
```

### Pattern 3: Batch Processing
```java
@Traced(value = "process-batch", operation = "batch")
public void processBatch(List<Order> orders) {
    for (Order order : orders) {
        tracingUtil.traceVoidOperation("process-order-" + order.getId(),
            () -> processOrder(order));
    }
}
```

## 🚀 Testing

```bash
# Test tracing setup
./scripts/test-tracing.sh

# Make test request with correlation ID
curl -H "X-Correlation-ID: test-123" \
     http://localhost:8080/actuator/health

# Check Zipkin for traces
open http://localhost:9411
```

## 📞 Support

- Documentation: `/docs/TRACING_SETUP.md`
- Zipkin Guide: `/docs/ZIPKIN_DASHBOARDS.md`
- Common Library README: `/common-library/src/main/java/com/ecommerce/common/tracing/README.md`

---

**💡 Pro Tip**: When debugging production issues, always ask users for the correlation ID from their error message. Search Zipkin by this ID to see the complete request flow.

**⚠️ Remember**: Traces tell you WHERE problems are, logs tell you WHY.
