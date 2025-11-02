# JDK Flight Recorder (JFR) Setup for Virtual Thread Monitoring

## Overview

JDK Flight Recorder (JFR) is a profiling and event collection framework built into the JDK. It's ideal for monitoring virtual threads and detecting thread pinning issues in production with minimal overhead (<1%).

## Prerequisites

- Java 21+ (Virtual threads support)
- JDK Mission Control (for GUI analysis) - Optional
- Services running in Docker or locally

## Quick Start

### 1. Enable JFR for a Service

```bash
# Record for 60 seconds (default)
./scripts/monitoring/enable-jfr.sh order-service

# Record for custom duration (e.g., 300 seconds)
./scripts/monitoring/enable-jfr.sh order-service 300
```

### 2. Analyze the Recording

```bash
./scripts/monitoring/analyze-jfr.sh ./jfr-recordings/order-service_20250102_120000.jfr
```

## Configuration Files

### JFR Configuration Template

Location: `config/jfr/virtual-threads-monitoring.jfc`

This configuration enables:
- ✅ Virtual thread lifecycle events
- ✅ Thread pinning detection (threshold: 20ms)
- ✅ Monitor contention tracking
- ✅ I/O operation profiling
- ✅ CPU and memory metrics
- ✅ GC events

### Customizing JFR Settings

Edit `config/jfr/virtual-threads-monitoring.jfc`:

```xml
<!-- Adjust pinning detection threshold -->
<event name="jdk.VirtualThreadPinned">
  <setting name="threshold">10 ms</setting>  <!-- Lower = more sensitive -->
</event>

<!-- Enable detailed exception tracking -->
<event name="jdk.JavaExceptionThrow">
  <setting name="enabled">true</setting>
</event>
```

## Docker Integration

### Option 1: Mount JFR Config (Recommended for Development)

Add to service in `docker-compose.yml`:

```yaml
services:
  order-service:
    volumes:
      - ./config/jfr:/app/config/jfr:ro
    environment:
      JAVA_TOOL_OPTIONS: >
        -XX:StartFlightRecording=
        name=continuous,
        settings=/app/config/jfr/virtual-threads-monitoring.jfc,
        filename=/tmp/flight-recorder.jfr,
        maxsize=500M,
        maxage=24h
```

### Option 2: On-Demand Recording

Start recording after service is running:

```bash
# Find container ID
docker-compose ps -q order-service

# Get Java PID inside container
docker exec <container-id> jps

# Start recording
docker exec <container-id> jcmd <java-pid> JFR.start \
  name=on-demand \
  settings=/app/config/jfr/virtual-threads-monitoring.jfc \
  duration=60s \
  filename=/tmp/recording.jfr

# Copy recording
docker cp <container-id>:/tmp/recording.jfr ./recording.jfr
```

### Option 3: Automated with Scripts

Use the provided scripts (recommended):

```bash
# Enable JFR (handles everything automatically)
./scripts/monitoring/enable-jfr.sh order-service 120
```

## Analyzing Recordings

### Command-Line Analysis

```bash
# View all events
jfr print recording.jfr

# View only pinning events
jfr print --events jdk.VirtualThreadPinned recording.jfr

# View virtual thread lifecycle
jfr print --events jdk.VirtualThreadStart,jdk.VirtualThreadEnd recording.jfr

# Export to JSON
jfr print --json recording.jfr > recording.json

# Summary statistics
jfr summary recording.jfr
```

### Using JDK Mission Control (GUI)

1. Download JMC: https://jdk.java.net/jmc/
2. Open recording: `jmc -open recording.jfr`
3. Navigate to:
   - **Java Application** → **Threads** → View virtual thread activity
   - **Event Browser** → Search for "VirtualThreadPinned"
   - **Method Profiling** → Identify hot methods

## Interpreting Results

### 1. Thread Pinning Events

```
jdk.VirtualThreadPinned {
  startTime = 14:23:45.123
  duration = 45.2 ms
  carrierThread = "ForkJoinPool-1-worker-3"
  stackTrace = [
    com.ecommerce.order.service.OrderService.createOrder(OrderService.java:42)
    ...
  ]
}
```

**What it means:**
- A virtual thread was pinned to its carrier thread for 45.2ms
- Check the stack trace for synchronized blocks or native calls

**Action:**
- Review `OrderService.java:42`
- Replace `synchronized` with `ReentrantLock`
- Update to virtual thread-friendly libraries

### 2. Monitor Contention

High `jdk.JavaMonitorEnter` events indicate:
- Synchronized blocks in hot paths
- Potential for pinning
- Performance bottlenecks

**Action:**
- Refactor synchronized blocks
- Use concurrent collections (e.g., `ConcurrentHashMap`)
- Consider lock-free alternatives

### 3. I/O Patterns

Socket/File read/write events show:
- I/O operation frequency
- Where virtual threads provide benefits
- Long-running I/O operations

### 4. CPU Usage

Monitor CPU load during recording:
- Virtual threads should show low CPU for I/O-bound work
- High CPU may indicate CPU-bound work (not ideal for virtual threads)

## Performance Impact

JFR overhead is typically:
- **<1%** with production settings
- **1-3%** with detailed profiling
- **Negligible** for event-based recording

## Best Practices

### ✅ DO

1. **Enable JFR in staging/production**
   - Minimal overhead
   - Catch real-world pinning issues

2. **Use continuous recording**
   ```bash
   -XX:StartFlightRecording=name=continuous,maxsize=500M,maxage=24h
   ```
   - Always have last 24 hours available
   - Useful for troubleshooting

3. **Monitor pinning threshold**
   - Start with 20ms threshold
   - Lower to 10ms if you need more sensitivity
   - Ignore brief (<5ms) pinning events

4. **Analyze regularly**
   - Weekly in development
   - After major changes
   - When investigating performance issues

### ❌ DON'T

1. **Don't ignore pinning warnings**
   - Even small amounts can impact scalability
   - Fix them before production

2. **Don't use overly aggressive settings**
   - Threshold too low = noise
   - Too many events = overhead

3. **Don't forget to rotate recordings**
   - Use `maxsize` and `maxage` limits
   - Recordings can grow large

## Troubleshooting

### JFR Not Working in Docker

**Problem:** JFR events not captured

**Solution:**
```dockerfile
# Ensure JDK tools are available (use full JDK, not JRE)
FROM eclipse-temurin:21-jdk

# Verify jcmd and jfr are available
RUN which jcmd && which jfr
```

### Cannot Find Recording File

**Problem:** File not found in container

**Solution:**
```bash
# Check file was created
docker exec <container-id> ls -lh /tmp/

# Ensure write permissions
docker exec <container-id> touch /tmp/test.jfr
```

### High Memory Usage

**Problem:** JFR consuming too much memory

**Solution:**
```bash
# Reduce recording size
-XX:StartFlightRecording=maxsize=100M  # Smaller limit

# Reduce retention
-XX:StartFlightRecording=maxage=6h  # Shorter retention
```

## Monitoring Scenarios

### Scenario 1: Pre-Production Validation

Before deploying:

```bash
# 1. Start all services
docker-compose up -d

# 2. Enable JFR on critical services
./scripts/monitoring/enable-jfr.sh order-service 300
./scripts/monitoring/enable-jfr.sh payment-service 300
./scripts/monitoring/enable-jfr.sh inventory-service 300

# 3. Run load tests
# (your load testing tool here)

# 4. Analyze recordings
./scripts/monitoring/analyze-jfr.sh ./jfr-recordings/order-service_*.jfr
```

### Scenario 2: Production Troubleshooting

When investigating issues:

```bash
# 1. Take a 5-minute snapshot
./scripts/monitoring/enable-jfr.sh <service> 300

# 2. Reproduce the issue

# 3. Analyze immediately
./scripts/monitoring/analyze-jfr.sh ./jfr-recordings/<latest>.jfr

# 4. Deep dive with JMC if needed
jmc -open ./jfr-recordings/<latest>.jfr
```

### Scenario 3: Continuous Monitoring

For always-on monitoring:

```yaml
# docker-compose.yml
services:
  order-service:
    environment:
      JAVA_TOOL_OPTIONS: >
        -XX:StartFlightRecording=
        name=continuous,
        settings=/app/config/jfr/virtual-threads-monitoring.jfc,
        filename=/tmp/continuous.jfr,
        maxsize=500M,
        maxage=24h
```

## Integration with Grafana

Export JFR metrics to Prometheus:

1. Use JFR Prometheus exporter
2. Configure scraping
3. Create Grafana dashboards

See `docs/MONITORING_SETUP.md` for details.

## References

- [JDK Flight Recorder Documentation](https://docs.oracle.com/en/java/javase/21/jfapi/index.html)
- [JEP 328: Flight Recorder](https://openjdk.org/jeps/328)
- [Virtual Threads JEP 444](https://openjdk.org/jeps/444)
- [JFR Event Reference](https://bestsolution-at.github.io/jfr-doc/)

## Summary

JFR is essential for:
- ✅ Detecting virtual thread pinning
- ✅ Profiling with minimal overhead
- ✅ Production troubleshooting
- ✅ Performance optimization

Use the provided scripts and configuration for automated monitoring.
