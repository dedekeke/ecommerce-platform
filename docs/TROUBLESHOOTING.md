# Troubleshooting Guide

> Back to [README](../README.md).

This guide covers common issues and their solutions when running the E-Commerce Platform.

## Quick Diagnostics

```bash
# Check all service health
./scripts/check-services.sh

# View service logs
tail -f logs/<service-name>.log

# Check infrastructure containers
docker-compose ps

# Check Eureka registered services
curl http://localhost:8761/eureka/apps
```

## Common Issues

### 1. Service Won't Start

**Symptoms**: Service fails to start or crashes immediately.

**Check logs**:
```bash
tail -f logs/<service-name>.log
```

**Common causes**:

| Cause | Solution |
|-------|----------|
| Port already in use | `lsof -ti:<port> \| xargs kill -9` |
| Database not running | `docker-compose up -d` |
| Missing environment variables | Check `.env` file |
| JAR not built | Run `mvn clean install` |

### 2. Database Connection Failed

**Symptoms**: `Connection refused` or `Unable to acquire JDBC Connection`

**Solutions**:

```bash
# Check PostgreSQL
docker-compose ps postgres
docker exec -it postgres pg_isready

# Check MongoDB
docker-compose ps mongodb
docker exec -it mongodb mongosh --eval "db.adminCommand('ping')"

# Restart databases
docker-compose restart postgres mongodb

# Verify connection string in .env
cat .env | grep -E "(POSTGRES|MONGODB)"
```

### 3. Service Not Registering with Eureka

**Symptoms**: Service running but not visible in Eureka dashboard.

**Solutions**:

1. Check Eureka is running: http://localhost:8761
2. Verify configuration:
   ```yaml
   eureka:
     client:
       enabled: true
       service-url:
         defaultZone: http://localhost:8761/eureka/
   ```
3. Wait 30 seconds for registration
4. Check network connectivity between containers

### 4. Rate Limit Exceeded (429 Error)

**Symptoms**: `Too Many Requests` response from API Gateway.

**Solutions**:

```bash
# Check current limits in logs
grep "rate limit" logs/api-gateway.log

# Clear Redis rate limit keys
redis-cli keys "*rate*" | xargs redis-cli del

# Increase limits in application.yml
# replenishRate: 200 -> 500
# burstCapacity: 400 -> 1000
```

### 5. Circuit Breaker Open

**Symptoms**: Requests failing immediately with fallback responses.

**Check circuit breaker status**:
```bash
curl http://localhost:8084/actuator/health | jq '.components.circuitBreakers'
```

**Solutions**:

1. Check downstream service health
2. Wait for `waitDurationInOpenState` to expire
3. Restart affected services
4. Check Grafana dashboards for failure patterns

### 6. Kafka Connection Issues

**Symptoms**: `KafkaProducer closed` or `Failed to send message`

**Solutions**:

```bash
# Check Kafka is running
docker-compose ps kafka

# Check Kafka logs
docker-compose logs kafka

# Restart Kafka
docker-compose restart zookeeper kafka

# List topics
docker exec -it kafka kafka-topics --list --bootstrap-server localhost:9092
```

### 7. Elasticsearch Connection Failed

**Symptoms**: Search service fails to start or search returns errors.

**Solutions**:

```bash
# Check Elasticsearch health
curl http://localhost:9200/_cluster/health

# Check indices
curl http://localhost:9200/_cat/indices

# Restart Elasticsearch
docker-compose restart elasticsearch
```

### 8. Redis Connection Issues

**Symptoms**: Caching not working, rate limiting failing.

**Solutions**:

```bash
# Check Redis is running
docker-compose ps redis
redis-cli ping

# Check memory usage
redis-cli info memory

# Flush cache (development only!)
redis-cli FLUSHALL

# Monitor commands
redis-cli monitor
```

### 9. Out of Memory

**Symptoms**: `OutOfMemoryError` or service crashes.

**Solutions**:

1. Increase heap size:
   ```bash
   export MAVEN_OPTS="-Xmx1024m"
   ```

2. Edit run script:
   ```bash
   # In scripts/run-service.sh
   -Dspring-boot.run.jvmArguments="-Xmx1024m -Xms512m"
   ```

3. For Docker:
   ```yaml
   environment:
     JAVA_OPTS: "-Xmx1024m -Xms512m"
   ```

### 10. gRPC Connection Failed

**Symptoms**: Order creation fails with gRPC errors.

**Solutions**:

```bash
# Check gRPC ports are available
lsof -i:9090  # Promotion Service
lsof -i:9091  # Product Service
lsof -i:9092  # Inventory Service

# Verify gRPC client configuration
cat .env | grep GRPC

# Test gRPC endpoint (requires grpcurl)
grpcurl -plaintext localhost:9090 list
```

### 11. JWT/Auth0 Validation Failed

**Symptoms**: `401 Unauthorized` on protected endpoints.

**Solutions**:

1. **For local development**: Set `SECURITY_ENABLED=false` in `.env`

2. **With Auth0**:
   ```bash
   # Verify Auth0 configuration
   curl https://<auth0-domain>/.well-known/openid-configuration

   # Check token
   echo $TOKEN | cut -d '.' -f2 | base64 -d | jq .
   ```

3. Check audience matches API identifier in Auth0

### 12. CORS Errors

**Symptoms**: Browser console shows CORS policy errors.

**Solutions**:

1. Verify origin is in allowed list (SecurityConfig.java)
2. Check preflight OPTIONS requests are passing
3. Add missing origin:
   ```java
   configuration.setAllowedOrigins(Arrays.asList(
       "http://localhost:3000",
       "<new-origin>"
   ));
   ```

## Service-Specific Issues

### Cart Service

| Issue | Solution |
|-------|----------|
| Cart not persisting | Check MongoDB connection |
| Cart expired prematurely | Verify TTL index configuration |
| Guest cart not merging | Check user ID extraction from JWT |

### Order Service

| Issue | Solution |
|-------|----------|
| Order stuck in PENDING | Check inventory/payment service health |
| Saga rollback failing | Check compensation logic in logs |
| Duplicate order numbers | Verify sequence generator |

### Payment Service

| Issue | Solution |
|-------|----------|
| Payment timeout | Increase TimeLimiter timeout |
| Gateway unreachable | Check circuit breaker state |
| Refund failing | Verify original transaction ID |

### Search Service

| Issue | Solution |
|-------|----------|
| Products not indexed | Check Kafka consumer lag |
| Search returns no results | Verify index mapping |
| Slow queries | Check Elasticsearch cluster health |

## Performance Issues

### High Latency

1. Check database query performance:
   ```sql
   SELECT * FROM pg_stat_statements ORDER BY mean_time DESC LIMIT 10;
   ```

2. Check cache hit rate in Grafana

3. Review Zipkin traces for slow calls

### High Memory Usage

1. Check for memory leaks:
   ```bash
   jcmd <pid> GC.heap_dump heap.hprof
   ```

2. Analyze with Eclipse MAT or VisualVM

3. Review ThreadLocal usage (can cause issues with virtual threads)

### CPU Spikes

1. Thread dump:
   ```bash
   jstack <pid> > thread_dump.txt
   ```

2. Check for thread pinning (virtual threads)

3. Review Prometheus CPU metrics

## Monitoring Endpoints

| Endpoint | Purpose |
|----------|---------|
| `/actuator/health` | Service health status |
| `/actuator/metrics` | Prometheus metrics |
| `/actuator/info` | Application info |
| `/actuator/loggers` | Log level management |
| `/actuator/env` | Environment properties |

## Log Analysis

### Common Log Patterns

```bash
# Find errors
grep -i "error\|exception" logs/*.log

# Find slow requests
grep "duration=" logs/*.log | awk -F'duration=' '{print $2}' | sort -rn | head

# Find circuit breaker events
grep -i "circuit" logs/*.log

# Find rate limit events
grep "429\|rate limit" logs/*.log
```

### Enable Debug Logging

```yaml
logging:
  level:
    com.ecommerce: DEBUG
    org.springframework.web: DEBUG
```

Or at runtime:
```bash
curl -X POST "http://localhost:808X/actuator/loggers/com.ecommerce" \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel":"DEBUG"}'
```

## Getting Help

1. Check logs in `logs/` directory
2. Review Grafana dashboards at http://localhost:3000
3. Check Zipkin traces at http://localhost:9411
4. Review Eureka registrations at http://localhost:8761
5. Consult service-specific documentation in `docs/`

## Related Documentation

- [QUICKSTART.md](../QUICKSTART.md) - Getting started guide
- [scripts/README.md](../scripts/README.md) - Script documentation
- [RESILIENCE_PATTERNS.md](RESILIENCE_PATTERNS.md) - Circuit breaker configuration
- [CACHING_STRATEGY.md](CACHING_STRATEGY.md) - Redis caching
- [SECURITY.md](SECURITY.md) - Security configuration
