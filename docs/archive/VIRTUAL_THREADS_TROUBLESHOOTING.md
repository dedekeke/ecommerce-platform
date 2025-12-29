# Virtual Threads Troubleshooting Guide

## Common Issues and Solutions

### Issue 1: "Could not autowire. No beans of 'MeterRegistry' type found"

**Problem:**
When using `VirtualThreadMonitoringConfig` in a service that doesn't have Spring Boot Actuator enabled, you may see autowiring errors for `MeterRegistry`.

**Solution:**
The `VirtualThreadMonitoringConfig` is already configured with `@ConditionalOnClass(MeterRegistry.class)`, which means it will only activate when Micrometer is on the classpath. To enable monitoring:

1. **Add Spring Boot Actuator to your service:**
   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-actuator</artifactId>
   </dependency>
   ```

2. **Or disable monitoring for that service:**
   ```yaml
   # application.yml
   virtual-threads:
     monitoring:
       enabled: false
   ```

3. **Or simply ignore** - if actuator is not present, the monitoring config will not be loaded (no error).

**Why this happens:**
- The monitoring configuration requires Micrometer (included in Spring Boot Actuator)
- Services without actuator won't have `MeterRegistry` bean
- The `@ConditionalOnClass` ensures the config is only loaded when Micrometer is available

---

### Issue 2: Virtual Threads Not Working

**Symptoms:**
- Thread count remains high
- No virtual threads visible in logs
- Performance not improved

**Solutions:**

1. **Verify Java 21+ is being used:**
   ```bash
   java -version
   # Should show: openjdk version "21" or higher
   ```

2. **Check virtual threads are enabled:**
   ```yaml
   # application.yml
   spring:
     threads:
       virtual:
         enabled: true
   ```

3. **Verify thread type at runtime:**
   ```java
   logger.info("Is virtual thread: {}", Thread.currentThread().isVirtual());
   ```

4. **Check Spring Boot version:**
   - Requires Spring Boot 3.2+ for full virtual thread support
   - Check `pom.xml` for Spring Boot version

---

### Issue 3: Thread Pinning Warnings

**Symptoms:**
```
Potential thread pinning detected - Thread: VirtualThread-42 | State: BLOCKED
```

**Solutions:**

1. **Find synchronized blocks:**
   ```bash
   grep -r "synchronized" --include="*.java" src/
   ```

2. **Replace with ReentrantLock:**
   ```java
   // BEFORE (causes pinning)
   public synchronized void update() {
       data.put(key, value);
   }

   // AFTER (no pinning)
   private final ReentrantLock lock = new ReentrantLock();

   public void update() {
       lock.lock();
       try {
           data.put(key, value);
       } finally {
           lock.unlock();
       }
   }
   ```

3. **Check for native methods:**
   - Some JDK methods may pin (being improved in newer versions)
   - Use JFR to identify specific methods causing pinning

---

### Issue 4: High Memory Usage Despite Virtual Threads

**Symptoms:**
- Memory usage higher than expected
- OOM errors
- Heap dumps show many ThreadLocal instances

**Solutions:**

1. **Review ThreadLocal usage:**
   ```java
   // AVOID: ThreadLocal with virtual threads
   private static final ThreadLocal<UserContext> context = new ThreadLocal<>();

   // BETTER: Use ScopedValue (Java 21+)
   private static final ScopedValue<UserContext> context = ScopedValue.newInstance();
   ```

2. **Limit virtual thread creation:**
   ```java
   // Don't create unlimited threads
   try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
       // Submit bounded number of tasks
       for (int i = 0; i < 10000; i++) {
           executor.submit(task);
       }
   }
   ```

3. **Profile memory usage:**
   ```bash
   # Heap dump
   jcmd <pid> GC.heap_dump heap.hprof

   # Analyze with VisualVM or Eclipse MAT
   ```

---

### Issue 5: Performance Worse with Virtual Threads

**Symptoms:**
- Slower than platform threads
- Higher CPU usage
- Increased latency

**Solutions:**

1. **Verify workload is I/O-bound:**
   ```bash
   # Profile CPU usage with JFR
   jcmd <pid> JFR.start settings=profile duration=60s
   ```

2. **Check for CPU-bound operations:**
   ```java
   // BAD: CPU-intensive work on virtual threads
   @Async
   public void processImage(BufferedImage img) {
       // Heavy CPU work - use platform threads!
   }

   // GOOD: Use dedicated thread pool for CPU work
   @Async("cpuBoundExecutor")
   public void processImage(BufferedImage img) {
       // CPU work on platform threads
   }
   ```

3. **Ensure sufficient platform threads:**
   - Virtual threads need platform threads (carriers)
   - Default: ~CPU cores
   - May need tuning for specific workloads

---

### Issue 6: Database Connection Pool Exhaustion

**Symptoms:**
```
Could not get JDBC Connection
Connection timeout
```

**Solutions:**

1. **Increase pool size (moderately):**
   ```yaml
   spring:
     datasource:
       hikari:
         maximum-pool-size: 50  # Was: 10
         connection-timeout: 30000
   ```

2. **Don't make pool too large:**
   - Virtual threads make better use of connections
   - 50-100 is usually sufficient vs 1000s with platform threads

3. **Monitor pool usage:**
   ```java
   // Check HikariCP metrics in Grafana
   hikaricp.connections.active
   hikaricp.connections.idle
   hikaricp.connections.pending
   ```

---

### Issue 7: Compilation Errors with VirtualThreadUtil

**Symptoms:**
```
cannot find symbol: VirtualThreadUtil
```

**Solutions:**

1. **Add common-library dependency:**
   ```xml
   <dependency>
       <groupId>com.ecommerce</groupId>
       <artifactId>common-library</artifactId>
       <version>1.0.0-SNAPSHOT</version>
   </dependency>
   ```

2. **Import the class:**
   ```java
   import com.ecommerce.common.util.VirtualThreadUtil;
   ```

3. **Rebuild common-library:**
   ```bash
   cd common-library
   mvn clean install
   ```

---

### Issue 8: @Async Not Using Virtual Threads

**Symptoms:**
- `Thread.currentThread().isVirtual()` returns false in @Async method

**Solutions:**

1. **Ensure @EnableAsync is present:**
   ```java
   @SpringBootApplication
   @EnableAsync
   public class Application {
       // ...
   }
   ```

2. **Verify VirtualThreadConfig is loaded:**
   ```bash
   # Check logs for
   "VirtualThreadConfig loaded"
   ```

3. **Check dependency on common-library:**
   - Common-library provides VirtualThreadConfig
   - Must be on classpath

---

### Issue 9: JFR Not Detecting Virtual Threads

**Symptoms:**
- `jfr print --events jdk.VirtualThreadStart` shows no events

**Solutions:**

1. **Ensure Java 21+:**
   ```bash
   java -version
   ```

2. **Use correct JFR configuration:**
   ```bash
   ./scripts/monitoring/enable-jfr.sh service-name 60
   ```

3. **Verify JFR is enabled:**
   ```bash
   jcmd <pid> JFR.check
   ```

---

### Issue 10: Docker Container Issues

**Symptoms:**
- Virtual threads work locally but not in Docker
- Thread count unexpectedly high in container

**Solutions:**

1. **Use Java 21+ base image:**
   ```dockerfile
   FROM eclipse-temurin:21-jdk
   # Not: FROM openjdk:11
   ```

2. **Verify Java version in container:**
   ```bash
   docker exec <container> java -version
   ```

3. **Check environment variables:**
   ```yaml
   # docker-compose.yml
   environment:
     JAVA_TOOL_OPTIONS: "-XX:+UnlockExperimentalVMOptions"
   ```

---

## Debugging Techniques

### 1. Enable Debug Logging

```yaml
# application.yml
logging:
  level:
    com.ecommerce.common.config.VirtualThreadConfig: DEBUG
    com.ecommerce.common.config.VirtualThreadMonitoringConfig: DEBUG
```

### 2. Check Thread Counts

```bash
# In running container
docker exec <container> jcmd <pid> Thread.print | grep -c "VirtualThread"

# Or via JMX
jconsole <pid>  # Connect and check thread count
```

### 3. Analyze with JFR

```bash
# Record
./scripts/monitoring/enable-jfr.sh service-name 60

# Analyze
./scripts/monitoring/analyze-jfr.sh recording.jfr

# Look for:
# - VirtualThreadStart events
# - VirtualThreadPinned events
# - JavaMonitorEnter events
```

### 4. Monitor Metrics

```bash
# Check Prometheus metrics
curl http://localhost:8080/actuator/prometheus | grep jvm.threads

# Should show:
# jvm_threads_virtual
# jvm_threads_platform
# jvm_threads_total
```

---

## Getting Help

### Internal Documentation
- `docs/VIRTUAL_THREADS_GUIDE.md` - Comprehensive guide
- `docs/JFR_MONITORING_SETUP.md` - JFR setup
- `docs/PERFORMANCE_TESTING.md` - Performance testing
- `docs/VIRTUAL_THREADS_QUICK_REF.md` - Quick reference

### External Resources
- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [Spring Boot 3.2 Virtual Threads](https://spring.io/blog/2023/09/09/all-together-now-spring-boot-3-2-graalvm-native-images-java-21-and-virtual)
- [Java 21 Virtual Threads Tutorial](https://inside.java/2023/04/28/virtual-threads-1/)

### Debug Checklist

Before opening an issue, verify:

- [ ] Java 21+ is being used
- [ ] Spring Boot 3.2+ is being used
- [ ] `spring.threads.virtual.enabled=true` is set
- [ ] Common-library dependency is included
- [ ] No synchronized blocks in code
- [ ] Actuator is enabled (for monitoring)
- [ ] JFR recording shows VirtualThreadStart events
- [ ] Workload is I/O-bound, not CPU-bound

---

## Prevention Tips

### ✅ DO

1. **Use provided utilities:**
   ```java
   VirtualThreadUtil.executeAllConcurrently(tasks);
   ```

2. **Monitor continuously:**
   - Set up Grafana dashboards
   - Enable JFR in staging/prod
   - Review metrics weekly

3. **Test thoroughly:**
   - Run performance tests before deploying
   - Test with realistic load
   - Verify no pinning occurs

### ❌ DON'T

1. **Don't assume it's always better:**
   - Profile first
   - Measure actual performance
   - CPU-bound work may be slower

2. **Don't ignore warnings:**
   - Pinning warnings indicate problems
   - Fix before production

3. **Don't use outdated libraries:**
   - Some libraries may not be virtual thread-friendly
   - Update dependencies regularly

---

## Summary

Most issues with virtual threads stem from:
1. Not using Java 21+
2. Synchronized blocks causing pinning
3. CPU-bound workloads
4. ThreadLocal overuse
5. Missing dependencies

Follow this guide to resolve issues quickly and ensure optimal virtual thread performance.
