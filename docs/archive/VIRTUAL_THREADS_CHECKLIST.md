# Virtual Threads Implementation Checklist

Use this checklist when creating new services or adding virtual thread support to existing services.

## Prerequisites

- [ ] Java 21 or higher installed
- [ ] Spring Boot 3.2 or higher
- [ ] Common-library dependency added

## Service Setup

### 1. Add Dependencies

- [ ] Add common-library to `pom.xml`:
```xml
<dependency>
    <groupId>com.ecommerce</groupId>
    <artifactId>common-library</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

- [ ] Add Spring Boot Actuator (for monitoring):
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### 2. Enable Virtual Threads

- [ ] Add to `application.yml`:
```yaml
spring:
  threads:
    virtual:
      enabled: true
```

### 3. Enable Async Support

- [ ] Add `@EnableAsync` to main application class:
```java
@SpringBootApplication
@EnableAsync
public class YourServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourServiceApplication.class, args);
    }
}
```

## Code Implementation

### 4. Use Async Annotations

- [ ] Mark async methods with `@Async`:
```java
@Service
public class YourService {
    @Async
    public CompletableFuture<Result> asyncMethod() {
        // Automatically uses virtual threads
        return CompletableFuture.completedFuture(result);
    }
}
```

### 5. Avoid Thread Pinning

- [ ] Search for synchronized blocks:
```bash
grep -r "synchronized" --include="*.java" src/
```

- [ ] Replace synchronized with ReentrantLock:
```java
// Replace this:
public synchronized void method() { }

// With this:
private final ReentrantLock lock = new ReentrantLock();
public void method() {
    lock.lock();
    try {
        // code
    } finally {
        lock.unlock();
    }
}
```

- [ ] Review ThreadLocal usage - minimize or use ScopedValue

### 6. Use Virtual Thread Utilities

- [ ] Import VirtualThreadUtil for concurrent operations:
```java
import com.ecommerce.common.util.VirtualThreadUtil;

// Concurrent execution
List<Result> results = VirtualThreadUtil.executeAllConcurrently(
    tasks.stream()
        .map(task -> () -> processTask(task))
        .toList()
);
```

## Configuration

### 7. Configure Actuator Endpoints

- [ ] Enable health and metrics endpoints:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
  metrics:
    export:
      prometheus:
        enabled: true
```

### 8. Configure Monitoring

- [ ] Enable virtual thread monitoring (automatic if actuator is present)
- [ ] Or explicitly configure:
```yaml
virtual-threads:
  monitoring:
    enabled: true
```

## Database Configuration

### 9. Optimize Connection Pool

- [ ] Tune HikariCP for virtual threads:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50  # Lower than platform threads
      minimum-idle: 10
      connection-timeout: 30000
```

## Testing

### 10. Verify Virtual Threads

- [ ] Add test to verify virtual threads are being used:
```java
@Test
void shouldUseVirtualThreads() {
    CompletableFuture<Boolean> future = service.asyncMethod();
    // In the async method, verify:
    assertTrue(Thread.currentThread().isVirtual());
}
```

### 11. Performance Testing

- [ ] Run performance tests:
```bash
./scripts/monitoring/run-performance-test.sh
```

- [ ] Compare metrics before/after
- [ ] Target improvements:
  - [ ] 20-40% latency reduction
  - [ ] 60-80% memory savings
  - [ ] 5-10x concurrency increase

### 12. JFR Monitoring

- [ ] Record with JFR:
```bash
./scripts/monitoring/enable-jfr.sh your-service 60
```

- [ ] Analyze for pinning:
```bash
./scripts/monitoring/analyze-jfr.sh recording.jfr
```

- [ ] Verify no thread pinning events
- [ ] Verify virtual threads are being created

## Docker Configuration

### 13. Update Dockerfile

- [ ] Use Java 21 base image:
```dockerfile
FROM eclipse-temurin:21-jdk
```

- [ ] Copy JFR config (optional):
```dockerfile
COPY config/jfr /app/config/jfr
```

### 14. Update docker-compose.yml

- [ ] Add service to docker-compose
- [ ] Include JFR volume mount (optional):
```yaml
volumes:
  - ./config/jfr:/app/config/jfr:ro
```

- [ ] Add JFR environment variable (optional for continuous monitoring):
```yaml
environment:
  JAVA_TOOL_OPTIONS: >
    -XX:StartFlightRecording=
    name=continuous,
    settings=/app/config/jfr/virtual-threads-monitoring.jfc,
    filename=/tmp/continuous.jfr,
    maxsize=500M,
    maxage=24h
```

## Monitoring & Observability

### 15. Grafana Dashboard

- [ ] Add service to Prometheus scraping config
- [ ] Verify metrics are being collected:
  - [ ] `jvm.threads.total`
  - [ ] `jvm.threads.platform`
  - [ ] `jvm.threads.virtual`
  - [ ] `jvm.threads.peak`

- [ ] Create/update Grafana dashboard

### 16. Logging

- [ ] Configure structured logging:
```yaml
logging:
  level:
    com.ecommerce.your.service: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

- [ ] Add correlation IDs to logs (from Zipkin/Sleuth)

## Documentation

### 17. Document Service-Specific Usage

- [ ] Document which operations use virtual threads
- [ ] Document expected performance characteristics
- [ ] Note any special considerations

## Pre-Production Checklist

### 18. Final Verification

- [ ] Virtual threads enabled: `spring.threads.virtual.enabled=true` ✓
- [ ] No synchronized blocks in code ✓
- [ ] Database pool sized appropriately ✓
- [ ] Actuator metrics enabled ✓
- [ ] JFR recording shows no pinning ✓
- [ ] Performance tests show improvement ✓
- [ ] Grafana dashboard configured ✓
- [ ] Documentation updated ✓

### 19. Load Testing

- [ ] Test with realistic load (1000+ concurrent requests)
- [ ] Monitor resource usage (CPU, memory, threads)
- [ ] Verify no errors at high concurrency
- [ ] Check database connection pool usage
- [ ] Verify response times meet SLA

### 20. Production Readiness

- [ ] Review logs for any thread-related warnings
- [ ] Ensure monitoring alerts are configured
- [ ] Document rollback plan
- [ ] Train team on virtual thread monitoring
- [ ] Set up continuous JFR recording (optional)

## Post-Deployment

### 21. Monitor in Production

- [ ] Watch thread metrics in Grafana
- [ ] Monitor for pinning events
- [ ] Track performance improvements
- [ ] Collect JFR recordings periodically

### 22. Optimization

- [ ] Review JFR recordings weekly
- [ ] Optimize slow operations
- [ ] Tune connection pool if needed
- [ ] Update documentation with learnings

## Troubleshooting Reference

If issues arise, refer to:
- `docs/VIRTUAL_THREADS_TROUBLESHOOTING.md` - Common issues
- `docs/VIRTUAL_THREADS_QUICK_REF.md` - Quick reference
- `docs/VIRTUAL_THREADS_GUIDE.md` - Comprehensive guide

## Quick Commands

```bash
# Compile service
mvn clean install

# Start service
docker-compose up -d your-service

# Check virtual threads in use
docker exec your-service jcmd <pid> Thread.print | grep -c Virtual

# Record JFR
./scripts/monitoring/enable-jfr.sh your-service 60

# Analyze JFR
./scripts/monitoring/analyze-jfr.sh recording.jfr

# Performance test
./scripts/monitoring/run-performance-test.sh

# View metrics
curl http://localhost:8080/actuator/prometheus | grep jvm.threads
```

---

## Service-Specific Notes

### For I/O-Heavy Services (recommended)
- User Service (database queries)
- Product Service (database + cache)
- Order Service (database + gRPC)
- Payment Service (external API calls)

### For Mixed Workload Services
- Media Service (file I/O + some CPU for image processing)
  - Use virtual threads for file I/O
  - Use platform thread pool for image processing

### For CPU-Heavy Services (use with caution)
- Search Service (Elasticsearch indexing)
  - Monitor carefully
  - May not see significant improvement

---

## Success Criteria

✅ Virtual threads enabled and working
✅ No thread pinning detected
✅ Performance improved vs baseline
✅ Memory usage reduced
✅ Monitoring in place
✅ Team trained on monitoring

---

Use this checklist for every new service to ensure consistent virtual thread implementation across the platform.
