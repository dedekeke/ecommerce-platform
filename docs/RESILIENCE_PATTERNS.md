# Resilience Patterns

> Back to [README](../README.md).

This document describes the resilience patterns implemented in the e-commerce platform using Resilience4j.

## Overview

The platform implements several resilience patterns to ensure fault tolerance and graceful degradation:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         REQUEST FLOW                                     │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  Request → Bulkhead → TimeLimiter → CircuitBreaker → Retry → Service    │
│                                                                          │
│  ┌──────────┐   ┌────────────┐   ┌───────────────┐   ┌───────┐          │
│  │ Bulkhead │ → │ TimeLimiter│ → │ CircuitBreaker│ → │ Retry │ → Call   │
│  │(Concurrency│  │ (Timeout)  │   │(Fail Fast)    │   │(Retry)│          │
│  │ Control)  │   │            │   │               │   │       │          │
│  └──────────┘   └────────────┘   └───────────────┘   └───────┘          │
│        │              │                 │                │               │
│        ▼              ▼                 ▼                ▼               │
│   Rejected if    Timeout if        Opens if        Retries on           │
│   max concurrent slow response     failure rate    transient            │
│   calls reached  exceeds limit     exceeds limit   failures             │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Implemented Patterns

### 1. Circuit Breaker

**Purpose**: Prevents cascading failures by failing fast when a downstream service is unhealthy.

**States**:
- `CLOSED`: Normal operation, requests pass through
- `OPEN`: Service is unhealthy, requests fail immediately
- `HALF_OPEN`: Testing if service recovered, limited requests allowed

**Configuration**:
```yaml
resilience4j:
  circuitbreaker:
    instances:
      service-name:
        slidingWindowSize: 10         # Number of calls to evaluate
        minimumNumberOfCalls: 5       # Min calls before calculating failure rate
        failureRateThreshold: 50      # % failures to open circuit
        slowCallRateThreshold: 80     # % slow calls to open circuit
        slowCallDurationThreshold: 3s # Definition of "slow"
        waitDurationInOpenState: 30s  # Time before half-open
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
```

### 2. Retry

**Purpose**: Automatically retries failed requests with exponential backoff.

**Configuration**:
```yaml
resilience4j:
  retry:
    instances:
      service-name:
        maxAttempts: 3
        waitDuration: 500ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
```

**Backoff Sequence** (with multiplier 2):
1. First retry: 500ms
2. Second retry: 1000ms
3. Third retry: 2000ms

### 3. Time Limiter

**Purpose**: Enforces timeout on potentially slow operations.

**Configuration**:
```yaml
resilience4j:
  timelimiter:
    instances:
      service-name:
        timeoutDuration: 5s
        cancelRunningFuture: true
```

### 4. Bulkhead

**Purpose**: Limits concurrent calls to prevent resource exhaustion.

**Configuration**:
```yaml
resilience4j:
  bulkhead:
    instances:
      service-name:
        maxConcurrentCalls: 25
        maxWaitDuration: 0ms  # Fail immediately if limit reached
```

## Service-Specific Configurations

### Order Service

The Order Service communicates with multiple downstream services:

| Service | Circuit Breaker | Retry | Timeout | Bulkhead |
|---------|-----------------|-------|---------|----------|
| Cart Service | 50% failure, 20s wait | 3 attempts | 3s | 20 concurrent |
| Inventory Service | 60% failure, 30s wait | 3 attempts | 5s | 30 concurrent |
| Payment Service | 40% failure, 60s wait | 2 attempts | 10s | 15 concurrent |
| Promotion Service | 70% failure, 15s wait | 3 attempts | 3s | 20 concurrent |

**Rationale**:
- **Cart Service**: Quick responses expected, fail fast
- **Inventory Service**: Slightly higher tolerance, stock checks are critical
- **Payment Service**: Lower failure threshold (40%), longer timeout for payment processing
- **Promotion Service**: Higher failure tolerance (70%), graceful degradation allowed

### Payment Service

| Service | Circuit Breaker | Retry | Timeout | Bulkhead |
|---------|-----------------|-------|---------|----------|
| Payment Gateway | 30% failure, 120s wait | 2 attempts | 30s | 10 concurrent |

**Rationale**:
- Very strict failure threshold (30%) - payment gateway failures are critical
- Long wait in open state (120s) - gateway outages typically last minutes
- Long timeout (30s) - payment processing can be slow
- Limited concurrency (10) - rate limiting for external gateway

### Cart Service (added 2026-07-14, PR #109)

The cart-service `ProductServiceGateway` (Feign call to product-service) is guarded by a
circuit breaker + bulkhead, mirroring order-service's annotation-based convention.

| Downstream | Circuit Breaker | Bulkhead | Fallback |
|------------|-----------------|----------|----------|
| Product Service | 50% failure, COUNT_BASED window 10, min 5 calls (env-tunable via `CB_DEFAULT_*`) | Semaphore | **Fail fast** — typed `ProductServiceUnavailableException` → HTTP 503 with `Retry-After` |

**Rationale**:
- When product-service browns out, add-to-cart/update calls fail fast instead of burning the read timeout, protecting the checkout funnel and the request thread pool
- Genuine 4xx responses (e.g. product not found) are re-thrown unchanged and do **not** trip the breaker; `ignoreExceptions`-only semantics ensure bulkhead rejections still count as breaker failures
- No stale prices or fabricated product data are ever served; cart read/view paths use denormalized `CartItem` data and never call product-service

## Fallback Strategies

### Graceful Degradation

Each protected method has a fallback that returns a sensible default:

```java
@CircuitBreaker(name = "promotion-service", fallbackMethod = "validatePromotionFallback")
public DiscountResult validatePromotion(PromotionValidationRequest request) { ... }

private DiscountResult validatePromotionFallback(PromotionValidationRequest request, Throwable t) {
    // Return invalid result - order proceeds without discount
    return DiscountResult.builder()
        .valid(false)
        .message("Promotion service unavailable")
        .discountAmount(BigDecimal.ZERO)
        .finalAmount(request.getPurchaseAmount())
        .build();
}
```

### Fallback Behaviors by Service

| Service | Fallback Behavior |
|---------|-------------------|
| Cart Service | Return empty cart response |
| Inventory Service | Return reservation failed (order cannot proceed) |
| Payment Service | Return payment failed (order cannot proceed) |
| Promotion Service | Return invalid discount (order proceeds without discount) |
| Payment Gateway | Return gateway unavailable (payment queued for retry) |

## Implementation Details

### Order Service - ResilientGrpcClient

```java
@Component
public class ResilientGrpcClient {

    @CircuitBreaker(name = "cart-service", fallbackMethod = "getCartFallback")
    @Retry(name = "cart-service")
    @Bulkhead(name = "cart-service")
    public GetCartResponse getCart(String userId) {
        return cartServiceStub.getCart(request);
    }

    private GetCartResponse getCartFallback(String userId, Throwable t) {
        log.error("Cart Service unavailable: {}", t.getMessage());
        return GetCartResponse.newBuilder()
            .setSuccess(false)
            .setMessage("Cart service temporarily unavailable")
            .build();
    }
}
```

### Annotation Order

The order of annotations matters - they execute in reverse order:

```java
@Bulkhead(name = "service")     // 1. Check concurrent limit
@TimeLimiter(name = "service")  // 2. Apply timeout
@CircuitBreaker(name = "service") // 3. Check circuit state
@Retry(name = "service")        // 4. Retry on failure
public Response callService() { ... }
```

## Monitoring

### Grafana Dashboard

The **Circuit Breaker Monitoring** dashboard shows:

1. **Circuit Breaker States**: Visual indicator (green/yellow/red) for each service
2. **Successful/Failed Call Rates**: Time series of call outcomes
3. **Failure Rate %**: Current failure rate with threshold lines
4. **Retry Calls**: Successful retries vs failed retries
5. **Bulkhead Available Slots**: Current capacity

### Prometheus Metrics

Key metrics exported:

```
# Circuit Breaker
resilience4j_circuitbreaker_state{name="service"} # 0=closed, 1=half_open, 2=open
resilience4j_circuitbreaker_failure_rate{name="service"}
resilience4j_circuitbreaker_calls_total{name="service",kind="successful|failed"}

# Retry
resilience4j_retry_calls_total{name="service",kind="successful_with_retry|failed_with_retry"}

# Bulkhead
resilience4j_bulkhead_available_concurrent_calls{name="service"}
resilience4j_bulkhead_max_allowed_concurrent_calls{name="service"}
```

### Alerts

Configure alerts for:
- Circuit breaker state changes (OPEN/HALF_OPEN)
- Failure rate > threshold
- Bulkhead saturation

## Testing Resilience

### Simulating Failures

1. **Stop downstream service**:
   ```bash
   docker stop cart-service
   ```

2. **Observe circuit breaker opening**:
   - Watch Grafana dashboard
   - Check application logs

3. **Verify fallback execution**:
   - Orders should still be processed (with limitations)
   - Fallback messages in logs

4. **Restart service and verify recovery**:
   ```bash
   docker start cart-service
   ```
   - Circuit should transition to HALF_OPEN then CLOSED

### Load Testing

Test bulkhead limits with concurrent requests:

```bash
# Send 50 concurrent requests
for i in {1..50}; do
  curl -X POST http://localhost:8084/api/orders &
done
```

Monitor for rejected requests (bulkhead limit exceeded).

## Best Practices

1. **Tune thresholds based on SLAs**:
   - Stricter for critical services (payment)
   - More lenient for optional services (promotions)

2. **Use meaningful fallbacks**:
   - Provide useful information in fallback responses
   - Log fallback invocations for monitoring

3. **Configure different instances per service**:
   - Each downstream service has unique characteristics
   - One-size-fits-all doesn't work

4. **Monitor and adjust**:
   - Watch Grafana dashboards in production
   - Adjust thresholds based on actual behavior

5. **Test failure scenarios**:
   - Regularly simulate failures in non-production
   - Verify fallback behavior works correctly

## Troubleshooting

### Circuit Breaker Stuck Open

1. Check downstream service health
2. Verify `waitDurationInOpenState` setting
3. Check if `automaticTransitionFromOpenToHalfOpenEnabled` is true

### Retries Not Working

1. Verify exception type is in `retryExceptions` list
2. Check that exceptions are not in `ignoreExceptions`
3. Verify `maxAttempts` > 1

### High Latency Due to Retries

1. Reduce `maxAttempts`
2. Lower `waitDuration`
3. Set stricter `timeoutDuration` in TimeLimiter

### Bulkhead Rejections

1. Increase `maxConcurrentCalls`
2. Check for connection pool issues
3. Investigate why calls are slow
