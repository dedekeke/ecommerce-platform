# Virtual Threads Implementation Guide

> Back to [README](../README.md).

## Overview

This e-commerce platform leverages **Java 21 Virtual Threads** (JEP 444) to improve scalability and performance for I/O-bound operations. Virtual threads are lightweight threads that allow applications to handle thousands or even millions of concurrent operations with minimal overhead.

## What are Virtual Threads?

Virtual threads are user-mode threads scheduled by the Java runtime rather than the operating system. They:
- Are extremely lightweight (compared to platform threads)
- Have shallow call stacks
- Can be created in massive numbers without degrading performance
- Allow simple blocking code to scale like asynchronous code

## Where Virtual Threads Provide Benefits

### ✅ Ideal Use Cases

Virtual threads excel in **I/O-bound operations**:

#### 1. Database Operations
```java
@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    // Virtual threads make blocking database calls efficient
    public User getUserById(Long id) {
        return userRepository.findById(id).orElseThrow();
    }

    // Concurrent database queries
    public List<User> getUsersByIds(List<Long> ids) {
        return VirtualThreadUtil.executeAllConcurrently(
            ids.stream()
                .map(id -> () -> userRepository.findById(id).orElse(null))
                .toList()
        );
    }
}
```

**Services benefiting:**
- User Service (PostgreSQL queries)
- Product Service (MySQL queries)
- Order Service (complex joins and transactions)
- Payment Service (transactional operations)
- Inventory Service (stock lookups)

#### 2. REST API Calls (External Services)
```java
@Service
public class PaymentService {
    private final RestTemplate restTemplate;

    // Virtual threads make HTTP calls non-blocking in nature
    public PaymentResponse processPayment(PaymentRequest request) {
        return restTemplate.postForObject(
            "https://payment-gateway.com/api/process",
            request,
            PaymentResponse.class
        );
    }
}
```

**Services benefiting:**
- Payment Service (payment gateway calls)
- Notification Service (SendGrid/Twilio API calls)
- Media Service (S3/CDN operations)

#### 3. gRPC Calls (Internal Service Communication)
```java
@Service
public class OrderService {
    private final CartServiceGrpc.CartServiceBlockingStub cartClient;
    private final InventoryServiceGrpc.InventoryServiceBlockingStub inventoryClient;
    private final PaymentServiceGrpc.PaymentServiceBlockingStub paymentClient;

    public Order createOrder(CreateOrderRequest request) {
        // All these gRPC calls benefit from virtual threads
        var cart = cartClient.getCart(GetCartRequest.newBuilder()
            .setUserId(request.getUserId())
            .build());

        var reservation = inventoryClient.reserveStock(/* ... */);
        var payment = paymentClient.createPaymentIntent(/* ... */);

        return saveOrder(cart, reservation, payment);
    }
}
```

**Services benefiting:**
- Order Service (calls Cart, Inventory, Payment services)
- Cart Service (may call Product Service for validation)

#### 4. Kafka Operations
```java
@Service
public class EventPublisher {
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    // Virtual threads make Kafka publishing efficient
    @Async
    public void publishEvent(String topic, Object event) {
        kafkaTemplate.send(topic, event);
    }
}
```

**Services benefiting:**
- All services publishing events
- Notification Service (Kafka consumers)
- Search Service (indexing events)

#### 5. Redis Operations
```java
@Service
public class CacheService {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // Virtual threads make cache operations efficient
    public Optional<Product> getCachedProduct(String productId) {
        return Optional.ofNullable(
            redisTemplate.opsForValue().get("product:" + productId)
        );
    }
}
```

**Services benefiting:**
- Product Service (catalog caching)
- Promotion Service (active promotions cache)
- API Gateway (rate limiting)

#### 6. File I/O Operations
```java
@Service
public class MediaService {
    // Virtual threads make file operations efficient
    public String saveFile(MultipartFile file) throws IOException {
        Path path = Paths.get("/storage/" + file.getOriginalFilename());
        Files.write(path, file.getBytes());
        return path.toString();
    }
}
```

**Services benefiting:**
- Media Service (image upload/download)

### ❌ Not Suitable For

#### 1. CPU-Intensive Operations
```java
// BAD - Don't use virtual threads for CPU-intensive work
public BufferedImage resizeImage(BufferedImage image) {
    // Image processing is CPU-bound
    // Use a dedicated thread pool with platform threads
    return ImageProcessor.resize(image, 800, 600);
}

// GOOD - Use a separate executor for CPU-intensive tasks
@Configuration
public class CpuTaskConfig {
    @Bean("cpuBoundExecutor")
    public Executor cpuBoundExecutor() {
        int processors = Runtime.getRuntime().availableProcessors();
        return Executors.newFixedThreadPool(processors);
    }
}
```

#### 2. Short-lived Operations
```java
// Overkill - Virtual threads have overhead for very quick operations
public int add(int a, int b) {
    return a + b; // Too simple for virtual threads
}
```

## Thread Pinning: The Main Pitfall

**Thread pinning** occurs when a virtual thread monopolizes its carrier thread (platform thread), preventing other virtual threads from running on it.

### Causes of Thread Pinning

#### 1. Synchronized Blocks ❌

```java
// BAD - synchronized causes thread pinning
public class BadInventoryService {
    private final Map<String, Integer> stock = new HashMap<>();

    public synchronized void updateStock(String productId, int quantity) {
        stock.put(productId, quantity); // PINS THE CARRIER THREAD!
    }
}
```

**Solution:** Use `ReentrantLock` instead:

```java
// GOOD - ReentrantLock is virtual thread friendly
public class GoodInventoryService {
    private final Map<String, Integer> stock = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    public void updateStock(String productId, int quantity) {
        lock.lock();
        try {
            stock.put(productId, quantity); // No pinning!
        } finally {
            lock.unlock();
        }
    }
}
```

#### 2. Native Methods

Some JDK methods that use native code may cause pinning:
- `Object.wait()`
- Some file I/O operations (being improved in newer Java versions)

Most modern I/O operations (NIO, async HTTP clients) are virtual thread-friendly.

### Identifying Synchronized Blocks

Let's check our codebase for synchronized blocks:

```bash
# Find all synchronized blocks in the codebase
grep -r "synchronized" --include="*.java" services/ common-library/src/
```

## Configuration in This Project

### 1. Global Virtual Thread Enablement

All services have virtual threads enabled in `application.yml`:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

**Files:**
- `infrastructure/eureka-server/src/main/resources/application.yml`
- `infrastructure/config-server/src/main/resources/application.yml`
- `infrastructure/api-gateway/src/main/resources/application.yml`
- All future service `application.yml` files

### 2. Async Executors

The `VirtualThreadConfig` class in `common-library` provides:

```java
@Bean
public AsyncTaskExecutor applicationTaskExecutor() {
    return new TaskExecutorAdapter(
        Executors.newVirtualThreadPerTaskExecutor()
    );
}
```

This makes all `@Async` methods use virtual threads automatically.

### 3. Custom Executors

For specific use cases:

```java
@Autowired
@Qualifier("virtualThreadExecutor")
private AsyncTaskExecutor virtualThreadExecutor;

@Autowired
@Qualifier("ioTaskExecutor")
private AsyncTaskExecutor ioTaskExecutor;
```

## Monitoring Virtual Threads

### 1. Metrics

The `VirtualThreadMonitoringConfig` registers these metrics:
- `jvm.threads.total` - Total thread count
- `jvm.threads.platform` - Platform thread count
- `jvm.threads.virtual` - Virtual thread count
- `jvm.threads.peak` - Peak thread count

View in Grafana at: http://localhost:3000

### 2. Logging

Thread statistics are logged every minute:
```
Thread Statistics - Total: 1523, Platform: 23, Virtual: 1500, Peak: 1523
```

Pinning warnings are logged every 5 minutes if detected:
```
Potential thread pinning detected - Thread: VirtualThread-42 | State: BLOCKED
```

### 3. JDK Flight Recorder

Enable JFR to detect pinning events:

```bash
# Start application with JFR enabled
java -XX:StartFlightRecording=filename=recording.jfr,duration=60s -jar app.jar

# View recording
jfr print --events jdk.VirtualThreadPinned recording.jfr
```

**Add to Docker Compose:**
```yaml
services:
  order-service:
    environment:
      JAVA_TOOL_OPTIONS: >
        -XX:StartFlightRecording=
        name=continuous,
        filename=/tmp/flight-recorder.jfr,
        maxsize=500M,
        maxage=24h,
        settings=profile
```

### 4. JFR Event Configuration

Create `jfr-config.jfc` for virtual thread monitoring:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration version="2.0" label="Virtual Thread Profiling">
  <event name="jdk.VirtualThreadStart">
    <setting name="enabled">true</setting>
    <setting name="stackTrace">true</setting>
  </event>
  <event name="jdk.VirtualThreadEnd">
    <setting name="enabled">true</setting>
  </event>
  <event name="jdk.VirtualThreadPinned">
    <setting name="enabled">true</setting>
    <setting name="stackTrace">true</setting>
    <setting name="threshold">20 ms</setting>
  </event>
</configuration>
```

Use it:
```bash
java -XX:StartFlightRecording=settings=jfr-config.jfc -jar app.jar
```

## Best Practices

### DO ✅

1. **Use virtual threads for I/O operations**
   ```java
   @Async
   public void processOrder(Order order) {
       // Database, gRPC, HTTP calls - all good!
   }
   ```

2. **Use ReentrantLock instead of synchronized**
   ```java
   private final ReentrantLock lock = new ReentrantLock();
   ```

3. **Write simple blocking code**
   ```java
   // Simple and efficient with virtual threads
   var user = userRepository.findById(id);
   var orders = orderRepository.findByUserId(id);
   return new UserProfile(user, orders);
   ```

4. **Use virtual thread utilities**
   ```java
   VirtualThreadUtil.executeAllConcurrently(tasks);
   ```

5. **Monitor for pinning**
   - Check logs for pinning warnings
   - Use JFR for detailed analysis
   - Review Grafana dashboards

### DON'T ❌

1. **Don't use synchronized blocks**
   ```java
   // BAD
   public synchronized void update() { }
   ```

2. **Don't use virtual threads for CPU-intensive work**
   ```java
   // BAD
   @Async // Uses virtual threads
   public void complexCalculation() {
       // Heavy CPU work
   }
   ```

3. **Don't use ThreadLocal excessively**
   - Virtual threads can create millions of instances
   - Each gets its own ThreadLocal copy
   - Can cause memory issues

4. **Don't use pooled virtual threads**
   ```java
   // BAD - defeats the purpose
   Executors.newFixedThreadPool(100); // Use platform threads if pooling

   // GOOD
   Executors.newVirtualThreadPerTaskExecutor(); // Create on demand
   ```

## Performance Testing

### Test Scenarios

1. **Baseline: Platform Threads**
   - 1000 concurrent requests
   - Measure response time, throughput, resource usage

2. **Virtual Threads**
   - Same 1000 concurrent requests
   - Compare metrics

3. **Stress Test**
   - 10,000 concurrent requests
   - Virtual threads should handle gracefully
   - Platform threads may exhaust resources

### JMeter Test Plan

See `performance-tests/virtual-threads-test.jmx` for detailed test plan.

Key metrics to compare:
- Average response time
- 95th percentile latency
- Throughput (requests/second)
- Memory usage
- Thread count
- CPU usage

### Expected Improvements

With virtual threads, expect:
- **2-3x** reduction in memory usage (fewer platform threads)
- **10-100x** more concurrent connections
- **Similar or better** response times
- **Lower** resource consumption at high concurrency

## Migration Checklist

For each service:

- [x] Enable `spring.threads.virtual.enabled=true`
- [x] Add `VirtualThreadConfig` from common-library
- [ ] Search for `synchronized` blocks → Replace with `ReentrantLock`
- [ ] Review `ThreadLocal` usage
- [ ] Add virtual thread monitoring
- [ ] Enable JFR for pinning detection
- [ ] Run performance tests
- [ ] Monitor in production

## Troubleshooting

### Issue: High Memory Usage

**Cause:** Excessive ThreadLocal usage with millions of virtual threads

**Solution:**
- Review ThreadLocal variables
- Use ScopedValues (Java 21+) instead
- Limit virtual thread creation

### Issue: Performance Not Improved

**Cause:** Code is CPU-bound, not I/O-bound

**Solution:**
- Profile with JFR
- Identify CPU hotspots
- Use platform thread pool for CPU-intensive tasks

### Issue: Thread Pinning Warnings

**Cause:** Synchronized blocks or native methods

**Solution:**
- Review stack traces from JFR
- Replace synchronized with ReentrantLock
- Update libraries to virtual thread-friendly versions

## References

- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [Virtual Threads: New Foundations for High-Scale Java Applications](https://www.infoq.com/articles/java-virtual-threads/)
- [Spring Boot 3.2+ Virtual Thread Support](https://spring.io/blog/2023/09/09/all-together-now-spring-boot-3-2-graalvm-native-images-java-21-and-virtual)
- [Java 21 Virtual Threads - Pitfalls and Best Practices](https://inside.java/2023/04/28/virtual-threads-1/)

## Summary

Virtual threads are enabled across all services in this e-commerce platform. They provide:
- Improved scalability for I/O-bound operations
- Simplified concurrent programming model
- Better resource utilization
- Foundation for handling high traffic loads

Monitor the platform's virtual thread usage through Grafana dashboards and JFR recordings to ensure optimal performance.
