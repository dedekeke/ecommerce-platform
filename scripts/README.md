# Development Scripts

This directory contains shell scripts to help with local development and testing of the e-commerce platform.

## Quick Reference

| Script | Description | Usage |
|--------|-------------|-------|
| `setup-local-dev.sh` | Setup local development environment | `./setup-local-dev.sh` |
| `build-all.sh` | Build all services | `./build-all.sh [--test] [--deploy]` |
| `run-service.sh` | Run a single service | `./run-service.sh <service-name>` |
| `run-all-services.sh` | Run all services | `./run-all-services.sh` |
| `stop-all-services.sh` | Stop all running services | `./stop-all-services.sh` |
| `check-services.sh` | Check service health | `./check-services.sh` |
| `test-kafka-events.sh` | Test Kafka event infrastructure | `./test-kafka-events.sh` |
| `test-tracing.sh` | Test distributed tracing | `./test-tracing.sh` |

## Quick Start

### 1. Initial Setup

```bash
# Clone the repository
git clone <repository-url>
cd ecommerce-platform

# Copy environment variables
cp .env.example .env
# Edit .env with your configuration (Auth0, databases, etc.)

# Setup local development environment
./scripts/setup-local-dev.sh
```

This will:
- Start infrastructure services (PostgreSQL, MySQL, MongoDB, Redis, Kafka, Zipkin, Eureka)
- Create required databases
- Verify service health

### 2. Build the Project

```bash
# Build without tests (faster)
./scripts/build-all.sh

# Build with tests
./scripts/build-all.sh --test

# Build and deploy with Docker
./scripts/build-all.sh --deploy
```

### 3. Run Services

#### Option A: Run All Services (Background Mode)

```bash
./scripts/run-all-services.sh
```

Services will run in background with logs in `logs/` directory:

```bash
# View logs
tail -f logs/<service-name>.log

# View all logs
tail -f logs/*.log
```

#### Option B: Run Individual Service

```bash
./scripts/run-service.sh <service-name>
```

Available services:
- Infrastructure: `eureka-server`, `config-server`, `api-gateway`
- Microservices: `user-service`, `product-service`, `cart-service`, `order-service`, `payment-service`, `inventory-service`, `notification-service`, `search-service`, `media-service`, `promotion-service`

### 4. Check Services

```bash
./scripts/check-services.sh
```

This will check the health of all services and display:
- Running services (✅ green)
- Down services (❌ red)
- Total count and status

### 5. Stop Services

```bash
./scripts/stop-all-services.sh
```

---

## Available Services

### Infrastructure Services

| Service | Port | URL | Description |
|---------|------|-----|-------------|
| Eureka Server | 8761 | http://localhost:8761 | Service discovery |
| Config Server | 8888 | http://localhost:8888 | Configuration management |
| API Gateway | 8080 | http://localhost:8080 | API gateway and routing |

### Microservices

| Service | Port | Swagger UI | Description |
|---------|------|------------|-------------|
| User Service | 8081 | http://localhost:8081/swagger-ui.html | User management |
| Product Service | 8082 | http://localhost:8082/swagger-ui.html | Product catalog |
| Cart Service | 8083 | http://localhost:8083/swagger-ui.html | Shopping cart |
| Order Service | 8084 | http://localhost:8084/swagger-ui.html | Order processing |
| Payment Service | 8085 | http://localhost:8085/swagger-ui.html | Payment processing |
| Inventory Service | 8086 | http://localhost:8086/swagger-ui.html | Inventory management |
| Notification Service | 8087 | http://localhost:8087/swagger-ui.html | Email/SMS notifications |
| Search Service | 8089 | http://localhost:8089/swagger-ui.html | Product search (Elasticsearch) |
| Media Service | 8090 | http://localhost:8090/swagger-ui.html | File upload/storage |
| Promotion Service | 8091 | http://localhost:8091/swagger-ui.html | Discounts & promotions |

### Consolidated API Documentation

**Swagger UI (All Services)**: http://localhost:8080/swagger-ui.html

The API Gateway provides a single interface to access documentation for all 10 microservices.

### Monitoring & Observability

| Service | Port | URL | Credentials |
|---------|------|-----|-------------|
| Zipkin | 9411 | http://localhost:9411 | - |
| Grafana | 3000 | http://localhost:3000 | admin/admin |
| Prometheus | 9090 | http://localhost:9090 | - |

---

## Detailed Script Documentation

### setup-local-dev.sh

Sets up the local development environment by starting infrastructure services.

**Prerequisites:**
- Docker and docker-compose installed
- `.env` file configured

**What it does:**
1. Checks for required `.env` file
2. Verifies Docker is running
3. Creates required databases
4. Starts infrastructure services (PostgreSQL, MySQL, MongoDB, Redis, Kafka, Zipkin, Eureka)
5. Verifies service health

**Example:**
```bash
./scripts/setup-local-dev.sh
```

---

### build-all.sh

Builds all microservices using Maven with optional flags.

**Usage:**
```bash
./scripts/build-all.sh              # Build without tests (default)
./scripts/build-all.sh --test       # Build with tests
./scripts/build-all.sh --deploy     # Build and deploy with Docker
```

**What it does:**
1. Runs `mvn clean install` (with or without tests)
2. Compiles all services
3. Installs artifacts to local Maven repository
4. Optionally starts Docker containers

**Build time:** ~2-5 minutes (without tests), ~5-10 minutes (with tests)

---

### run-service.sh

Runs a single microservice in the foreground.

**Usage:**
```bash
./scripts/run-service.sh <service-name>
```

**Examples:**
```bash
./scripts/run-service.sh cart-service
./scripts/run-service.sh user-service
./scripts/run-service.sh eureka-server
```

**What it does:**
1. Validates service name
2. Determines service path (services/ or infrastructure/)
3. Sets Spring profile to "local"
4. Runs service with Maven spring-boot plugin
5. Configures JVM with 512MB max heap

**JVM Options:**
- `-Xmx512m` - Maximum heap size
- `-Xms256m` - Initial heap size

---

### run-all-services.sh

Runs all microservices in background mode.

**Usage:**
```bash
./scripts/run-all-services.sh
```

**What it does:**
1. Starts each service in background
2. Redirects output to `logs/<service>.log`
3. Saves PID to `logs/<service>.pid`
4. Delays 5 seconds between services

**Service Startup Order:**
1. Eureka Server (8761)
2. Config Server (8888)
3. User Service (8081)
4. Product Service (8082)
5. Cart Service (8083)
6. Order Service (8084)
7. Payment Service (8085)
8. Inventory Service (8086)
9. Notification Service (8087)
10. Search Service (8089)
11. Media Service (8090)
12. Promotion Service (8091)
13. API Gateway (8080)

---

### stop-all-services.sh

Stops all running microservices.

**Usage:**
```bash
./scripts/stop-all-services.sh
```

**What it does:**
1. Stops processes using PID files in `logs/` directory
2. Kills remaining Spring Boot processes
3. Cleans up PID files

---

### check-services.sh

Checks the health status of all services.

**Usage:**
```bash
./scripts/check-services.sh
```

**What it does:**
1. Pings health endpoints of all services
2. Displays status for each service
3. Shows summary (total, running, down)
4. Returns exit code 1 if any service is down

---

### test-kafka-events.sh

Tests the Kafka event infrastructure.

**Usage:**
```bash
./scripts/test-kafka-events.sh
```

**What it does:**
1. Checks Kafka availability
2. Lists existing topics
3. Verifies event topics exist
4. Publishes test events
5. Checks consumer groups
6. Monitors DLQ topics

---

### test-tracing.sh

Tests distributed tracing with Zipkin.

**Usage:**
```bash
./scripts/test-tracing.sh
```

**What it does:**
1. Verifies Zipkin is running
2. Checks registered services in Eureka
3. Generates test traces
4. Verifies traces appear in Zipkin
5. Tests correlation ID propagation

---

### Monitoring Scripts

Located in `scripts/monitoring/`:

#### enable-jfr.sh
```bash
./scripts/monitoring/enable-jfr.sh <service-name> [duration]
```
Enables JDK Flight Recorder for virtual thread monitoring.

#### analyze-jfr.sh
```bash
./scripts/monitoring/analyze-jfr.sh <recording-file.jfr>
```
Analyzes JFR recordings for thread pinning and performance issues.

#### run-performance-test.sh
```bash
./scripts/monitoring/run-performance-test.sh [api-url]
```
Runs performance tests comparing virtual threads vs platform threads.

---

### Testing Scripts

Located in `scripts/testing/`:

#### run-integration-tests.sh
```bash
./scripts/testing/run-integration-tests.sh [test-name]
```
Runs integration tests with menu interface.

**Options:**
- `all` - Run all tests
- `order-flow` - Order flow tests
- `saga` - Saga compensation tests
- `concurrent` - Concurrent order tests
- `performance` - Performance tests
- `load` - Load tests

---

## Environment Variables

All scripts use environment variables from `.env` file:

```bash
# Load environment variables
export $(cat .env | grep -v '^#' | xargs)
```

**Required Variables:**
- `SECURITY_ENABLED` - Enable/disable Auth0 (set to `false` for local dev)
- `POSTGRES_USER` - PostgreSQL username
- `POSTGRES_PASSWORD` - PostgreSQL password
- `MYSQL_USER` - MySQL username
- `MYSQL_PASSWORD` - MySQL password
- `AUTH0_DOMAIN` - Auth0 domain (if security enabled)
- `AUTH0_AUDIENCE` - Auth0 API audience (if security enabled)
- `KAFKA_BOOTSTRAP_SERVERS` - Kafka broker address
- `REDIS_HOST` - Redis host
- `ELASTICSEARCH_URL` - Elasticsearch URL (for search service)

---

## Troubleshooting

### Port Already in Use

**Problem:** Service fails to start because port is in use

**Solution:**
```bash
# Find and kill process using port 8083
lsof -ti:8083 | xargs kill -9

# Or use different port
./scripts/run-service.sh cart-service -Dserver.port=8084
```

### Database Connection Failed

**Problem:** Service can't connect to database

**Solution:**
```bash
# Restart infrastructure
docker-compose down
./scripts/setup-local-dev.sh

# Check PostgreSQL
pg_isready -h localhost -p 5432

# Check MySQL
mysqladmin ping -h localhost

# Check MongoDB
mongosh --eval "db.adminCommand('ping')"
```

### Service Won't Stop

**Problem:** `stop-all-services.sh` doesn't stop all services

**Solution:**
```bash
# Force kill all Maven processes
pkill -9 -f maven

# Force kill all Java processes
pkill -9 -f "spring-boot:run"
```

### Out of Memory

**Problem:** Service crashes with OutOfMemoryError

**Solution:**
Increase heap size in `run-service.sh` or `run-all-services.sh`:

```bash
# Change from 512m to 1024m
-Dspring-boot.run.jvmArguments="-Xmx1024m -Xms512m"
```

### Service Not Registering with Eureka

**Problem:** Service starts but doesn't appear in Eureka dashboard

**Solution:**
1. Verify Eureka is running: http://localhost:8761
2. Check service logs for connection errors
3. Wait 30 seconds for registration to complete
4. Verify `eureka.client.enabled=true` in service config

### Kafka Connection Failed

**Problem:** Service can't connect to Kafka

**Solution:**
```bash
# Check Kafka is running
docker-compose ps kafka

# Restart Kafka
docker-compose restart kafka

# Check Kafka logs
docker-compose logs kafka
```

### Elasticsearch Connection Failed (Search Service)

**Problem:** Search service can't connect to Elasticsearch

**Solution:**
```bash
# Check Elasticsearch is running
curl http://localhost:9200

# Restart Elasticsearch
docker-compose restart elasticsearch

# Check logs
docker-compose logs elasticsearch
```

---

## Log Files

When running services in background mode, logs are stored in:

```
logs/
├── cart-service.log
├── cart-service.pid
├── user-service.log
├── user-service.pid
├── ... (all services)
```

**View logs:**
```bash
# Tail specific service
tail -f logs/cart-service.log

# Tail all services
tail -f logs/*.log

# Search logs
grep "ERROR" logs/cart-service.log

# View last 100 lines
tail -n 100 logs/cart-service.log
```

---

## Advanced Usage

### Running with Custom Profiles

```bash
# Run with docker profile
mvn spring-boot:run -pl services/cart-service -Dspring-boot.run.profiles=docker

# Run with multiple profiles
mvn spring-boot:run -pl services/cart-service -Dspring-boot.run.profiles=local,dev
```

### Running with Additional JVM Options

```bash
# Enable JMX monitoring
mvn spring-boot:run -pl services/cart-service \
  -Dspring-boot.run.jvmArguments="-Xmx512m -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9010"

# Enable remote debugging
mvn spring-boot:run -pl services/cart-service \
  -Dspring-boot.run.jvmArguments="-Xmx512m -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
```

### Running Specific Test

```bash
# Run single test
mvn test -pl services/cart-service -Dtest=CartServiceTest

# Run test with debug output
mvn test -pl services/cart-service -Dtest=CartServiceTest -X
```

---

## Integration with IDEs

### IntelliJ IDEA

Instead of using scripts, you can run services directly from IntelliJ IDEA:

1. Import project as Maven project
2. Create run configurations for each service
3. Use compound run configuration to start multiple services

See [IDEA_RUN_CONFIGURATIONS.md](../docs/IDEA_RUN_CONFIGURATIONS.md) for detailed instructions.

### VS Code

1. Install "Spring Boot Extension Pack"
2. Open Command Palette (Cmd+Shift+P)
3. Select "Spring Boot Dashboard: Open"
4. Click play button next to service name

---

## Performance Testing

### Load Testing

```bash
# Test specific endpoint
ab -n 10000 -c 100 http://localhost:8080/api/products

# With POST data
ab -n 1000 -c 50 -p order.json -T application/json http://localhost:8080/api/orders
```

### Virtual Threads Performance

```bash
# Run performance comparison
./scripts/monitoring/run-performance-test.sh

# Enable JFR monitoring
./scripts/monitoring/enable-jfr.sh order-service 60

# Analyze JFR recording
./scripts/monitoring/analyze-jfr.sh jfr-recordings/order-service_*.jfr
```

---

## Tips

1. **Start Eureka first** to avoid service registration delays
2. **Check logs** in `logs/` directory if services don't start
3. **Wait 30 seconds** after starting services for full initialization
4. **Set SECURITY_ENABLED=false** for easier local development without Auth0
5. **Use Swagger UI** at Gateway (http://localhost:8080/swagger-ui.html) to test all APIs
6. **Monitor Eureka dashboard** (http://localhost:8761) to verify service registration
7. **Use check-services.sh** regularly to ensure all services are healthy
8. **Check Grafana dashboards** (http://localhost:3000) for performance metrics
9. **Use build-all.sh --deploy** for quick Docker deployment
10. **Run tests with --test flag** before committing changes
