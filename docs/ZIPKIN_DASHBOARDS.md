# Zipkin Dashboards and Visualization Guide

This guide explains how to use Zipkin UI effectively to visualize and troubleshoot distributed traces in the e-commerce platform.

## Accessing Zipkin

**URL**: `http://localhost:9411`

## Dashboard Overview

The Zipkin UI consists of several key sections:

### 1. Search Page (Main Dashboard)

The main page where you search for traces.

**URL**: `http://localhost:9411/zipkin/`

#### Search Criteria

- **Service Name**: Filter by specific service (e.g., `order-service`, `api-gateway`)
- **Span Name**: Filter by operation name (e.g., `create-order`, `database.SELECT`)
- **Tags**: Custom tags added to spans (e.g., `http.status_code=500`, `error=true`)
- **Min Duration**: Find slow requests (e.g., `>1000ms`)
- **Max Duration**: Find fast requests
- **Lookback**: Time range (1h, 6h, 12h, 24h, 2d, 7d, custom)
- **Limit**: Number of traces to return (default: 10)

#### Common Search Queries

**Find All Errors:**
```
Tag: error=true
```

**Find Slow Order Creation:**
```
Service: order-service
Span Name: create-order
Min Duration: 1000ms
```

**Find Payment Failures:**
```
Service: payment-service
Tag: payment.status=FAILED
```

**Find Requests from Specific User:**
```
Tag: userId=12345
```

**Find Requests by Correlation ID:**
```
Tag: correlationId=abc-xyz-123
```

### 2. Trace Details View

Click on any trace to see detailed information.

#### Trace Timeline

Visualizes the complete request flow:

```
┌─ api-gateway (150ms) ───────────────────────────────┐
│  ├─ order-service.create-order (120ms) ─────────┐   │
│  │  ├─ grpc.CartService/getCart (20ms) ───┐    │   │
│  │  ├─ grpc.InventoryService/reserve (30ms)│    │   │
│  │  ├─ grpc.PaymentService/intent (40ms) ──│    │   │
│  │  ├─ database.INSERT (15ms) ──────────────│    │   │
│  │  └─ kafka.produce (10ms) ────────────────│    │   │
│  └─────────────────────────────────────────┘    │   │
└──────────────────────────────────────────────────┘   │
```

**Color Coding:**
- **Blue**: Service boundary
- **Green**: Success
- **Red**: Error
- **Gray**: Cache hit

#### Span Details

Each span shows:

**Header Information:**
- **Service Name**: Which service this span belongs to
- **Span Name**: Operation name
- **Duration**: How long it took
- **Start Time**: When it started

**Tags:**
- `http.method`: GET, POST, etc.
- `http.path`: /api/orders
- `http.status_code`: 200, 404, 500, etc.
- `error`: true (if failed)
- Custom business tags (e.g., `order.id`, `user.id`)

**Annotations:**
- Events that occurred during span execution
- Useful for marking milestones

**Example Span:**
```
Span: database.INSERT
Duration: 15ms
Tags:
  - db.operation: INSERT
  - db.table: orders
  - span.kind: client
  - order.id: ORD-2025-00123
  - status: success
```

### 3. Dependencies View

Visualizes service dependencies as a graph.

**URL**: `http://localhost:9411/zipkin/dependency`

Shows:
- Which services call which
- Request rates between services
- Error rates

**Example:**
```
           ┌──────────────┐
           │ api-gateway  │
           └──────┬───────┘
                  │
     ┌────────────┼────────────┐
     │            │            │
     ▼            ▼            ▼
┌─────────┐ ┌──────────┐ ┌─────────┐
│  user   │ │ product  │ │  cart   │
│ service │ │ service  │ │ service │
└─────────┘ └──────────┘ └────┬────┘
                               │
                               ▼
                         ┌──────────┐
                         │  order   │
                         │ service  │
                         └────┬─────┘
                              │
                    ┌─────────┼─────────┐
                    │         │         │
                    ▼         ▼         ▼
              ┌──────────┬──────────┬──────────┐
              │inventory │ payment  │notification│
              │ service  │ service  │  service   │
              └──────────┴──────────┴───────────┘
```

## Common Use Cases

### Use Case 1: Debugging Slow Requests

**Problem**: Users reporting slow checkout

**Steps:**
1. Go to Zipkin search
2. Set service: `order-service`
3. Set span: `create-order`
4. Set min duration: `1000ms`
5. Click "Run Query"
6. Click on slowest trace
7. Identify bottleneck (longest span)

**Example Finding:**
```
Trace: create-order (2500ms)
├─ grpc.CartService/getCart (50ms)
├─ grpc.InventoryService/reserve (80ms)
├─ grpc.PaymentService/intent (2200ms) ← BOTTLENECK
├─ database.INSERT (100ms)
└─ kafka.produce (70ms)
```

**Action**: Investigate Payment Service performance

### Use Case 2: Finding Errors

**Problem**: Seeing 500 errors in logs

**Steps:**
1. Search for: `error=true`
2. Or: `http.status_code=500`
3. Click on error trace
4. Look for red spans
5. Check error tags for exception details

**Example Error Trace:**
```
Span: grpc.PaymentService/createIntent
Status: ERROR
Tags:
  - error: true
  - error.message: "Insufficient funds"
  - payment.amount: 150.00
  - user.id: 12345
```

### Use Case 3: Tracking User Journey

**Problem**: Track specific user's requests

**Steps:**
1. Search for: `userId=12345`
2. Select time range
3. See all user's requests chronologically
4. Identify patterns or issues

**Example Traces:**
```
10:30:15 - GET /products (200ms)
10:30:45 - POST /cart/items (150ms)
10:31:20 - POST /cart/items (160ms)
10:32:10 - POST /orders (2500ms) ← SLOW
```

### Use Case 4: Monitoring Service Communication

**Problem**: Verify cart data is passed correctly to order service

**Steps:**
1. Search for: `create-order` span
2. Click on trace
3. Find `grpc.CartService/getCart` span
4. Check tags for cart data
5. Verify data in subsequent order span

**Example:**
```
grpc.CartService/getCart:
  Tags:
    - cart.itemCount: 3
    - cart.total: 150.00

database.INSERT (orders):
  Tags:
    - order.itemCount: 3
    - order.total: 150.00
  ✓ Data matches
```

### Use Case 5: Correlation ID Tracking

**Problem**: Customer reports issue, provides correlation ID from error message

**Steps:**
1. Search for: `correlationId=abc-xyz-123`
2. View complete request flow
3. Identify exactly where failure occurred
4. See all logs with same correlation ID

## Advanced Filtering

### Combining Filters

Search for slow payment failures:
```
Service: payment-service
Tags: error=true
Min Duration: 500ms
```

### Regular Expressions (in tags)

Not directly supported, but you can search for partial matches.

### Time-based Analysis

Compare traces across time periods:
1. Set lookback to 1 hour
2. Note average duration
3. Set lookback to different time
4. Compare patterns

## Performance Metrics from Traces

### Calculating Percentiles

1. Search for specific operation
2. Set limit to 100
3. Export results
4. Calculate p50, p95, p99 durations

### Service Latency Breakdown

For an order creation (150ms total):
- Cart retrieval: 20ms (13%)
- Inventory check: 30ms (20%)
- Payment intent: 40ms (27%)
- Database save: 15ms (10%)
- Event publish: 10ms (7%)
- Network/overhead: 35ms (23%)

## Trace Annotations

Look for these annotations in spans:

**Order Service:**
- "Order validation started"
- "Inventory check initiated"
- "Payment processing started"
- "Order persisted to database"
- "Order event published"

**Payment Service:**
- "Payment gateway called"
- "Payment confirmation received"
- "Payment record updated"

## Custom Dashboard Queries

### Top 10 Slowest Operations (Last Hour)

1. Service: (any)
2. Lookback: 1h
3. Limit: 100
4. Sort by duration descending

### Error Rate by Service

1. Tag: `error=true`
2. Lookback: 1h
3. Group by service name

### Most Active Services

1. Lookback: 1h
2. Limit: 100
3. Count spans by service

## Integration with Logs

### Linking Traces to Logs

Each trace has a trace ID. Use this to search logs:

**Zipkin Trace ID**: `abc123def456`

**Log Query** (if using centralized logging):
```
traceId:abc123def456
```

This shows all log entries for that request across all services.

### Correlation ID Workflow

1. User reports error
2. Error message includes correlation ID
3. Search Zipkin by correlation ID
4. Get trace ID from Zipkin
5. Search logs with trace ID
6. See complete picture

## Alerting Based on Traces

While Zipkin doesn't have built-in alerting, you can:

1. Export trace data to Prometheus
2. Create alerts based on:
   - P95 latency > threshold
   - Error rate > threshold
   - Specific service dependencies down

**Example Prometheus Query:**
```promql
histogram_quantile(0.95,
  sum(rate(http_server_requests_seconds_bucket{
    uri="/api/orders"
  }[5m])) by (le)
) > 1.0
```

## Trace Sampling Impact

Remember that traces are sampled:

- **Development**: 100% sampling - see all requests
- **Production**: 10% sampling - see 1 in 10 requests

If you don't see a specific request:
1. It might not have been sampled
2. Increase sampling temporarily
3. Or use correlation ID from logs

## Troubleshooting Zipkin

### No Traces Appearing

**Check:**
1. Is Zipkin running? `curl http://localhost:9411/health`
2. Are services configured correctly? Check `application.yml`
3. Is sampling > 0? Check `management.tracing.sampling.probability`
4. Check service logs for Zipkin errors
5. Verify network connectivity

### Incomplete Traces

**Possible Causes:**
1. Service not reporting to Zipkin
2. Trace context not propagated
3. Async operations not properly scoped
4. Service timeout before reporting

### Missing Service Names

**Fix:**
Ensure each service has unique `spring.application.name` in config.

## Best Practices

1. **Use Descriptive Span Names**: Help identify operations quickly
2. **Add Business Context Tags**: `order.id`, `user.id`, `product.id`
3. **Tag Errors Properly**: Always include error message
4. **Use Annotations for Milestones**: Mark important events
5. **Keep Sampling Reasonable**: Balance visibility and overhead
6. **Regular Review**: Check slowest operations weekly
7. **Set Up Alerts**: Don't rely on manual checking
8. **Train Your Team**: Everyone should know how to use Zipkin

## Quick Reference Card

```
┌─────────────────────────────────────────────────────────┐
│ Zipkin Quick Reference                                  │
├─────────────────────────────────────────────────────────┤
│ Main UI:        http://localhost:9411                   │
│ Dependencies:   http://localhost:9411/zipkin/dependency │
│ API:            http://localhost:9411/api/v2            │
├─────────────────────────────────────────────────────────┤
│ Common Searches:                                        │
│ - Errors:        error=true                             │
│ - Slow:          Min Duration > 1000ms                  │
│ - User:          userId=12345                           │
│ - Correlation:   correlationId=abc-123                  │
│ - Service:       Service name dropdown                  │
├─────────────────────────────────────────────────────────┤
│ Span Colors:                                            │
│ - Blue:   Service boundary                              │
│ - Green:  Success                                       │
│ - Red:    Error                                         │
│ - Gray:   Cache hit                                     │
└─────────────────────────────────────────────────────────┘
```

## Example Scenarios

### Scenario 1: New Service Not Appearing

**Problem**: Just deployed new service, not seeing it in Zipkin

**Checklist:**
- [ ] Service has `spring.application.name` set
- [ ] Tracing dependencies in pom.xml
- [ ] `management.tracing.enabled=true` in config
- [ ] Zipkin endpoint configured
- [ ] Service can reach Zipkin (network)
- [ ] Sampling probability > 0
- [ ] Service has received requests
- [ ] Waited 30 seconds for first trace

### Scenario 2: Optimizing Slow Endpoint

**Problem**: `/api/orders` taking 3+ seconds

**Investigation Steps:**
1. Search: `span=create-order`, `minDuration=3000ms`
2. Click slowest trace
3. Identify longest spans
4. Common culprits:
   - Database N+1 queries
   - Synchronous external API calls
   - Missing indexes
   - Large payload serialization

**Example Analysis:**
```
Total: 3200ms
├─ getUser: 50ms (OK)
├─ getCart: 2800ms (PROBLEM!)
│  └─ getItem: 2750ms
│     ├─ database.SELECT products: 2700ms ← N+1 QUERY!
│     └─ enrichment: 50ms
├─ createOrder: 300ms (OK)
└─ sendNotification: 50ms (OK)

Fix: Use JOIN FETCH to load products with cart
```

## Summary

Zipkin is a powerful tool for:
- Understanding request flows
- Identifying bottlenecks
- Debugging distributed systems
- Monitoring service health
- Tracking user journeys
- Correlating with logs

Master these searches:
1. Error traces: `error=true`
2. Slow requests: `minDuration > 1000ms`
3. User tracking: `userId=<id>`
4. Correlation: `correlationId=<id>`

Remember: Traces tell you **where** problems are, logs tell you **why**.
