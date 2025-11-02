# Virtual Threads Quick Reference Card

## 🚀 Quick Start

### Enable in Service
```yaml
# application.yml
spring:
  threads:
    virtual:
      enabled: true
```

### Use in Code
```java
// Option 1: @Async (automatic)
@Async
public CompletableFuture<Result> asyncMethod() {
    // Uses virtual threads automatically
}

// Option 2: VirtualThreadUtil
List<Result> results = VirtualThreadUtil.executeAllConcurrently(tasks);

// Option 3: Direct usage
Thread.startVirtualThread(() -> {
    // Your code here
});
```

## ✅ Use Virtual Threads For

| Use Case | Example | Benefit |
|----------|---------|---------|
| Database queries | `repository.findAll()` | High concurrency |
| REST API calls | `restTemplate.getForObject()` | Non-blocking I/O |
| gRPC calls | `cartClient.getCart()` | Efficient service calls |
| Kafka operations | `kafkaTemplate.send()` | Better throughput |
| Redis operations | `redisTemplate.get()` | Lower latency |
| File I/O | `Files.readAllBytes()` | Parallel processing |

## ❌ Don't Use Virtual Threads For

- CPU-intensive calculations
- Image/video processing
- Encryption/hashing
- Mathematical computations

## 🔧 Common Patterns

### Pattern 1: Concurrent Database Queries
```java
List<Product> products = VirtualThreadUtil.executeAllConcurrently(
    productIds.stream()
        .map(id -> () -> productRepository.findById(id).orElse(null))
        .toList()
);
```

### Pattern 2: Async Service Method
```java
@Service
public class OrderService {
    @Async
    public CompletableFuture<Order> createOrder(OrderRequest request) {
        // Runs on virtual thread
        return CompletableFuture.completedFuture(order);
    }
}
```

### Pattern 3: Multiple Service Calls
```java
CompletableFuture<Cart> cartFuture = VirtualThreadUtil.executeAsync(() ->
    cartClient.getCart(request)
);
CompletableFuture<Inventory> inventoryFuture = VirtualThreadUtil.executeAsync(() ->
    inventoryClient.checkStock(request)
);

// Wait for both
Cart cart = cartFuture.get();
Inventory inventory = inventoryFuture.get();
```

## 🚫 Avoid Thread Pinning

### ❌ BAD: synchronized block
```java
public synchronized void update() {
    // Pins carrier thread!
}
```

### ✅ GOOD: ReentrantLock
```java
private final ReentrantLock lock = new ReentrantLock();

public void update() {
    lock.lock();
    try {
        // No pinning
    } finally {
        lock.unlock();
    }
}
```

## 📊 Monitoring Commands

```bash
# Start JFR recording
./scripts/monitoring/enable-jfr.sh order-service 60

# Analyze recording
./scripts/monitoring/analyze-jfr.sh recording.jfr

# Run performance test
./scripts/monitoring/run-performance-test.sh
```

## 🔍 Debugging

### Check if running on virtual thread
```java
if (Thread.currentThread().isVirtual()) {
    logger.info("Running on virtual thread!");
}
```

### Log thread info
```java
VirtualThreadUtil.logThreadInfo("Creating order");
```

## 📈 Metrics

Monitor in Grafana (http://localhost:3000):
- `jvm.threads.virtual` - Virtual thread count
- `jvm.threads.platform` - Platform thread count
- `jvm.threads.peak` - Peak thread count

## 📚 Documentation

- Full Guide: `docs/VIRTUAL_THREADS_GUIDE.md`
- JFR Setup: `docs/JFR_MONITORING_SETUP.md`
- Performance Testing: `docs/PERFORMANCE_TESTING.md`
- Summary: `docs/DAY7_VIRTUAL_THREADS_SUMMARY.md`

## 🎯 Performance Targets

| Metric | Expected Improvement |
|--------|---------------------|
| Max Concurrency | 5-10x |
| Memory Usage | -60% to -80% |
| Latency (P95) | -20% to -40% |
| Thread Count | -99% (platform threads) |

## ⚠️ Common Issues

### Issue: Performance not improved
**Solution:** Profile with JFR, check for CPU-bound work

### Issue: High memory usage
**Solution:** Review ThreadLocal usage, check for memory leaks

### Issue: Thread pinning warnings
**Solution:** Replace synchronized with ReentrantLock

## 🔗 Quick Links

| Resource | Command |
|----------|---------|
| View thread stats | `docker logs <service> | grep "Thread Statistics"` |
| Check for pinning | `./scripts/monitoring/enable-jfr.sh <service> 60` |
| Performance test | `./scripts/monitoring/run-performance-test.sh` |
| Grafana | http://localhost:3000 |
| Zipkin | http://localhost:9411 |
