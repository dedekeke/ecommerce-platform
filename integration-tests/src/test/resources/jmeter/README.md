# JMeter Load Testing Guide

## Overview
This directory contains JMeter test plans for load testing the e-commerce platform.

## Prerequisites
1. Install Apache JMeter (5.6.2 or later)
   ```bash
   brew install jmeter  # On macOS
   # Or download from https://jmeter.apache.org/download_jmeter.cgi
   ```

2. Start all services via Docker Compose
   ```bash
   cd /path/to/ecommerce-platform
   docker-compose up -d
   ```

3. Verify services are running
   ```bash
   curl http://localhost:8080/actuator/health  # API Gateway
   curl http://localhost:8081/actuator/health  # User Service
   curl http://localhost:8082/actuator/health  # Product Service
   curl http://localhost:8083/actuator/health  # Cart Service
   curl http://localhost:8084/actuator/health  # Order Service
   ```

## Test Plans

### 1. Order Flow Load Test
Tests the complete order creation flow with 1000 concurrent users.

**Test Configuration:**
- Thread Count: 1000 users
- Ramp-up Period: 60 seconds (gradual increase)
- Loop Count: 1 iteration per user
- Total Requests: ~4000 (4 requests per user)

**Test Flow:**
1. Create User (POST /api/users)
2. Create Product (POST /api/products)
3. Add to Cart (POST /api/cart/{userId}/items)
4. Create Order (POST /api/orders)

**Run the test:**
```bash
jmeter -n -t OrderFlowLoadTest.jmx -l results/order-flow-results.jtl -e -o results/order-flow-report
```

### 2. Sustained Load Test
Tests system stability under sustained load for 10 minutes.

**Test Configuration:**
- Thread Count: 100 users
- Ramp-up Period: 30 seconds
- Loop Count: Infinite (duration-based)
- Duration: 600 seconds (10 minutes)

**Run the test:**
```bash
jmeter -n -t SustainedLoadTest.jmx -l results/sustained-load-results.jtl -e -o results/sustained-load-report
```

### 3. Spike Test
Tests system behavior under sudden traffic spikes.

**Test Configuration:**
- Thread Count: 500 users (spike)
- Ramp-up Period: 10 seconds (rapid increase)
- Loop Count: 2 iterations

**Run the test:**
```bash
jmeter -n -t SpikeTest.jmx -l results/spike-test-results.jtl -e -o results/spike-test-report
```

## Creating Custom Test Plans

### Using JMeter GUI
1. Start JMeter GUI:
   ```bash
   jmeter
   ```

2. Create a new Test Plan
3. Add Thread Group (right-click Test Plan → Add → Threads → Thread Group)
4. Configure thread properties:
   - Number of Threads: 1000
   - Ramp-up Period: 60
   - Loop Count: 1

5. Add HTTP Request Defaults (right-click Thread Group → Add → Config Element → HTTP Request Defaults)
   - Server Name: localhost
   - Port Number: 8080

6. Add HTTP Requests for each API call
7. Add Listeners (View Results Tree, Aggregate Report, etc.)
8. Save the test plan

### Programmatic Test Plan Creation
See `LoadTestRunner.java` for programmatic JMeter test creation using the JMeter API.

## Analyzing Results

### View HTML Report
After running a test, open the generated HTML report:
```bash
open results/order-flow-report/index.html
```

### Key Metrics to Monitor
- **Throughput**: Requests per second
- **Response Time**: Average, p95, p99
- **Error Rate**: Should be < 1%
- **Concurrent Users**: Number of active users
- **Resource Usage**: CPU, Memory, Database connections

### Performance Targets (from plan.md)
- API response time: p95 < 200ms
- gRPC call latency: p95 < 50ms
- Throughput: 100+ orders per minute
- Support: 1000+ concurrent users
- Error rate: < 1%

## JMeter Properties

### Configure Memory
Edit `$JMETER_HOME/bin/jmeter.sh` or set environment variable:
```bash
export HEAP="-Xms1g -Xmx4g -XX:MaxMetaspaceSize=256m"
```

### Distributed Testing
For testing with multiple JMeter instances:

1. Start JMeter server on remote machines:
   ```bash
   jmeter-server -Djava.rmi.server.hostname=<server-ip>
   ```

2. Run test from controller:
   ```bash
   jmeter -n -t test.jmx -R <server1-ip>,<server2-ip> -l results.jtl
   ```

## Monitoring During Tests

### Monitor Services
```bash
# CPU and Memory
docker stats

# Logs
docker-compose logs -f order-service

# Database connections
docker exec -it postgres psql -U admin -c "SELECT count(*) FROM pg_stat_activity;"
```

### Prometheus Metrics
Access Prometheus: http://localhost:9090

Key queries:
```promql
# Request rate
rate(http_server_requests_seconds_count[5m])

# Error rate
rate(http_server_requests_seconds_count{status=~"5.."}[5m])

# Response time p95
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))
```

### Grafana Dashboards
Access Grafana: http://localhost:3000 (admin/admin)

Pre-configured dashboards:
- Service Health Overview
- API Response Times
- Database Performance
- JVM Metrics

## Troubleshooting

### Connection Refused
```bash
# Check if services are running
docker-compose ps

# Check service health
curl http://localhost:8080/actuator/health
```

### High Error Rate
1. Check service logs: `docker-compose logs -f <service-name>`
2. Verify database connections
3. Check memory/CPU usage
4. Review error responses in JMeter results

### Slow Response Times
1. Check database query performance
2. Verify gRPC connections
3. Review circuit breaker states
4. Check for resource contention

## Best Practices

1. **Warm-up Period**: Start with a small load before ramping up
2. **Realistic Data**: Use varied test data (different users, products)
3. **Think Time**: Add delays between requests to simulate real users
4. **Assertions**: Add response assertions to validate correctness
5. **Cleanup**: Clean up test data after runs
6. **Monitor**: Always monitor system resources during tests
7. **Baseline**: Establish baseline performance before changes
8. **Incremental**: Increase load gradually to find breaking points

## Example: Running Complete Load Test

```bash
# 1. Start services
cd /path/to/ecommerce-platform
docker-compose up -d

# 2. Wait for services to be ready
sleep 60

# 3. Run load test
cd integration-tests/src/test/resources/jmeter
jmeter -n -t OrderFlowLoadTest.jmx -l results/results.jtl -e -o results/report

# 4. View results
open results/report/index.html

# 5. Check service health
docker-compose ps
docker stats --no-stream

# 6. Clean up
docker-compose down
```

## Resources
- [JMeter Documentation](https://jmeter.apache.org/usermanual/index.html)
- [JMeter Best Practices](https://jmeter.apache.org/usermanual/best-practices.html)
- [Distributed Testing](https://jmeter.apache.org/usermanual/jmeter_distributed_testing_step_by_step.html)
