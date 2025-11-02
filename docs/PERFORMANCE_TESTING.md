# Virtual Threads Performance Testing Guide

## Overview

This guide covers performance testing for virtual threads in the e-commerce platform. The tests compare virtual threads against platform threads to measure improvements in throughput, latency, and resource utilization.

## Test Scenarios

### 1. HTTP Request Load Test

**Purpose:** Measure throughput and latency under various concurrency levels

**Test Configuration:**
- Concurrency levels: 100, 500, 1000, 2000, 5000
- Requests per level: 1000
- Target: `/actuator/health` endpoint
- Timeout: 10 seconds

**Metrics Collected:**
- Average latency
- Min/Max latency
- P50, P95, P99 percentiles
- Success/failure rate
- Thread count
- Memory usage

### 2. Database Query Load Test

**Purpose:** Measure database operation performance with virtual threads

**Test Setup:**
```java
// Execute concurrent database queries
VirtualThreadUtil.executeAllConcurrently(
    productIds.stream()
        .map(id -> () -> productRepository.findById(id))
        .toList()
);
```

**Expected Results:**
- Better scalability at high concurrency
- Lower memory usage
- Similar or better latency

### 3. gRPC Call Load Test

**Purpose:** Test internal service communication performance

**Test Setup:**
```java
// Concurrent gRPC calls
for (int i = 0; i < 1000; i++) {
    VirtualThread.startVirtualThread(() -> {
        cartClient.getCart(request);
    });
}
```

**Metrics:**
- gRPC call latency
- Connection pool efficiency
- Resource utilization

### 4. Mixed Workload Test

**Purpose:** Simulate realistic e-commerce traffic

**Workload Mix:**
- 60% Product browsing (GET requests)
- 20% Cart operations (POST/PUT)
- 15% Order creation (POST with multiple service calls)
- 5% Search queries

## Running Tests

### Quick Start

```bash
# Ensure services are running
docker-compose up -d

# Wait for services to be healthy
./scripts/wait-for-services.sh

# Run performance tests
./scripts/monitoring/run-performance-test.sh
```

### Custom Configuration

```bash
# Test against specific API
./scripts/monitoring/run-performance-test.sh http://localhost:8080

# Set custom concurrency levels
API_URL=http://localhost:8080 \
CONCURRENCY_LEVELS="100,500,1000" \
./scripts/monitoring/run-performance-test.sh
```

### Using Java Directly

```bash
# Compile
javac -d target/test-classes performance-tests/VirtualThreadsPerformanceTest.java

# Run
java -cp target/test-classes \
  com.ecommerce.performance.VirtualThreadsPerformanceTest
```

## Analyzing Results

### Sample Output

```
=================================================================================
PERFORMANCE COMPARISON
=================================================================================

Concurrency     Type            Avg (ms)        P95 (ms)        P99 (ms)
---------------------------------------------------------------------------------
100             Platform        45              67              89
                Virtual         42              63              84
                Improvement     6.7%            6.0%

500             Platform        78              123             167
                Virtual         68              105             142
                Improvement     12.8%           14.6%

1000            Platform        156             234             312
                Virtual         121             189             245
                Improvement     22.4%           19.2%

2000            Platform        298             456             601
                Virtual         187             278             367
                Improvement     37.2%           39.0%

5000            Platform        724             1123            1456
                Virtual         389             612             798
                Improvement     46.3%           45.5%
```

### Key Insights

**✅ Good Performance Indicators:**
- Virtual threads show 20-50% improvement at high concurrency
- P95/P99 latencies are consistently lower
- Memory usage is significantly reduced
- No thread pinning detected in JFR

**⚠️ Warning Signs:**
- Virtual threads slower than platform threads → Check for CPU-bound work
- High P99 latencies → Investigate outliers
- Increasing failure rate → Check timeouts and resource limits

## Memory Usage Comparison

### Monitoring During Tests

```bash
# In one terminal, run tests
./scripts/monitoring/run-performance-test.sh

# In another terminal, monitor memory
watch -n 1 'docker stats --no-stream | grep -E "NAME|order-service"'
```

### Expected Memory Savings

| Concurrency | Platform Threads | Virtual Threads | Savings |
|-------------|------------------|-----------------|---------|
| 100         | ~200 MB          | ~150 MB         | 25%     |
| 500         | ~800 MB          | ~300 MB         | 62%     |
| 1000        | ~1.5 GB          | ~450 MB         | 70%     |
| 5000        | OOM Error        | ~800 MB         | N/A     |

**Why?**
- Platform thread stack: ~1 MB each
- Virtual thread stack: ~10 KB each
- At 5000 threads: 5 GB vs 50 MB!

## Thread Count Analysis

Monitor thread counts during tests:

```bash
# Get thread count from JVM
docker exec order-service jcmd <pid> Thread.print | grep "Thread count"

# Or use JFR
jfr print --events jdk.VirtualThreadStart,jdk.VirtualThreadEnd recording.jfr
```

### Expected Results

**Platform Threads:**
- Thread count = concurrency level
- Example: 1000 concurrent requests = ~1000 threads

**Virtual Threads:**
- Platform thread count remains low (~CPU cores)
- Virtual thread count = concurrency level
- Example: 1000 concurrent requests = ~1000 virtual threads on ~16 platform threads

## Load Testing with JMeter

### Test Plan Configuration

1. **Thread Group (Platform Threads Baseline)**
   - Threads: 100, 500, 1000
   - Ramp-up: 30s
   - Duration: 5 minutes

2. **HTTP Request Sampler**
   - Target: `http://localhost:8080/api/products`
   - Method: GET

3. **Assertions**
   - Response code: 200
   - Response time: < 200ms (P95)

4. **Listeners**
   - Summary Report
   - Response Time Graph
   - Transactions per Second

### Running JMeter Tests

```bash
# Install JMeter
brew install jmeter  # macOS
# or download from https://jmeter.apache.org/

# Run test plan
jmeter -n -t performance-tests/virtual-threads-test.jmx \
  -l results.jtl \
  -e -o report-output/

# View report
open report-output/index.html
```

## Stress Testing

### Gradually Increase Load

```bash
#!/bin/bash
for concurrency in 100 500 1000 2000 5000 10000; do
  echo "Testing with $concurrency concurrent users..."

  # Run test
  hey -n 10000 -c $concurrency http://localhost:8080/api/products

  # Cool down
  sleep 30
done
```

### Using `hey` Tool

```bash
# Install hey
go install github.com/rakyll/hey@latest

# Test with 1000 concurrent requests
hey -n 10000 -c 1000 -m GET http://localhost:8080/api/products

# With authorization
hey -n 10000 -c 1000 \
  -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/products
```

### Expected Breaking Points

**Platform Threads:**
- Starts degrading: ~1000 concurrent
- Severe issues: ~2000 concurrent
- Likely OOM: ~5000 concurrent

**Virtual Threads:**
- Stable up to: ~5000 concurrent
- Starts degrading: ~10000 concurrent
- Bottleneck shifts to: Database, network

## Database Performance Testing

### Connection Pool Configuration

```yaml
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50  # Lower than platform threads would need
      minimum-idle: 10
      connection-timeout: 30000
```

**With Virtual Threads:**
- Pool size can be much smaller
- Threads block efficiently while waiting for connections
- Better connection utilization

### Testing Database Load

```java
// Benchmark database queries
@Test
void testDatabaseConcurrency() {
    List<Callable<Product>> tasks = new ArrayList<>();
    for (int i = 0; i < 1000; i++) {
        int id = i;
        tasks.add(() -> productRepository.findById(id));
    }

    long start = System.currentTimeMillis();
    VirtualThreadUtil.executeAllConcurrently(tasks);
    long duration = System.currentTimeMillis() - start;

    System.out.println("1000 queries in: " + duration + "ms");
}
```

## Continuous Performance Testing

### Automated Nightly Tests

```yaml
# .github/workflows/performance-test.yml
name: Nightly Performance Tests

on:
  schedule:
    - cron: '0 2 * * *'  # 2 AM daily

jobs:
  performance-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2

      - name: Start services
        run: docker-compose up -d

      - name: Run performance tests
        run: ./scripts/monitoring/run-performance-test.sh

      - name: Upload results
        uses: actions/upload-artifact@v2
        with:
          name: performance-results
          path: performance-results/
```

### Performance Regression Detection

Compare results over time:

```bash
#!/bin/bash
# Compare with baseline
BASELINE="performance-results/baseline.txt"
CURRENT="performance-results/results_latest.txt"

# Extract P95 latencies
baseline_p95=$(grep "P95" $BASELINE | awk '{print $4}')
current_p95=$(grep "P95" $CURRENT | awk '{print $4}')

# Calculate regression
regression=$(( (current_p95 - baseline_p95) * 100 / baseline_p95 ))

if [ $regression -gt 10 ]; then
  echo "⚠️ Performance regression detected: ${regression}%"
  exit 1
fi
```

## Troubleshooting Performance Issues

### Issue 1: Virtual Threads Not Faster

**Possible Causes:**
- Code is CPU-bound, not I/O-bound
- Thread pinning occurring
- Test duration too short

**Solutions:**
```bash
# Check for pinning
./scripts/monitoring/enable-jfr.sh order-service 60
./scripts/monitoring/analyze-jfr.sh recording.jfr

# Profile CPU usage
jcmd <pid> JFR.start settings=profile duration=60s

# Identify CPU-intensive methods
```

### Issue 2: High Memory Usage

**Possible Causes:**
- Memory leaks
- ThreadLocal variables
- Large object allocations

**Solutions:**
```bash
# Heap dump
jcmd <pid> GC.heap_dump /tmp/heap.hprof

# Analyze with VisualVM or Eclipse MAT
```

### Issue 3: Database Connection Pool Exhaustion

**Symptoms:**
- Timeouts waiting for connections
- Slow query execution

**Solutions:**
```yaml
# Increase pool size (but not too much)
spring.datasource.hikari.maximum-pool-size: 100

# Reduce connection timeout
spring.datasource.hikari.connection-timeout: 20000
```

## Best Practices

### ✅ DO

1. **Test with realistic data**
   - Use production-like dataset sizes
   - Include complex queries

2. **Run tests multiple times**
   - Warm up JVM first
   - Average results over 3-5 runs

3. **Monitor all resources**
   - CPU, memory, network
   - Database connections
   - Thread counts

4. **Test failure scenarios**
   - Network delays
   - Database failures
   - Timeout handling

### ❌ DON'T

1. **Don't test on development machines**
   - Use dedicated test environment
   - Minimize background processes

2. **Don't ignore outliers**
   - Investigate P99+ latencies
   - Check for intermittent issues

3. **Don't test only happy paths**
   - Include error scenarios
   - Test with invalid data

## Metrics to Track

### Application Metrics
- ✅ Request throughput (req/s)
- ✅ Response time (avg, P95, P99)
- ✅ Error rate
- ✅ Active threads (platform vs virtual)

### System Metrics
- ✅ CPU usage
- ✅ Memory usage (heap, non-heap)
- ✅ GC activity
- ✅ Network I/O

### Database Metrics
- ✅ Connection pool utilization
- ✅ Query execution time
- ✅ Transaction throughput
- ✅ Lock contention

## Expected Performance Improvements

Based on virtual threads testing:

| Metric               | Improvement      | Notes                          |
|----------------------|------------------|--------------------------------|
| Max Concurrency      | 5-10x            | Before hitting other limits    |
| Memory Usage         | 60-80% reduction | At high concurrency            |
| Latency (P95)        | 20-40% reduction | For I/O-bound operations       |
| Throughput           | 2-3x             | At high concurrency            |
| Thread Count         | 100x reduction   | Platform threads               |

## Summary

Virtual threads provide significant performance improvements for I/O-bound operations:

- ✅ **Higher throughput** at high concurrency
- ✅ **Lower latency** (especially P95/P99)
- ✅ **Reduced memory usage** (60-80% savings)
- ✅ **Better scalability** (10x more concurrent operations)
- ✅ **Simpler code** (blocking code performs like async)

Use the provided tests and scripts to validate these improvements in your environment.
