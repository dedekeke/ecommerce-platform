# Quick Start Guide

This guide will help you get the E-Commerce Platform running locally in minutes.

## Prerequisites

Before you begin, ensure you have the following installed:

| Requirement | Version | Check Command |
|-------------|---------|---------------|
| Java JDK | 21+ | `java -version` |
| Maven | 3.8+ | `mvn -version` |
| Docker | Latest | `docker --version` |
| Docker Compose | Latest | `docker-compose --version` |
| Git | Latest | `git --version` |

## Step 1: Clone and Configure

```bash
# Clone the repository
git clone https://github.com/your-org/ecommerce-platform.git
cd ecommerce-platform

# Copy environment template
cp .env.template .env
```

### Configure Environment Variables

Edit `.env` and set the following (minimum required for local development):

```bash
# Disable Auth0 for local development
SECURITY_ENABLED=false

# Database credentials
POSTGRES_USER=admin
POSTGRES_PASSWORD=admin123
MYSQL_USER=admin
MYSQL_PASSWORD=admin123
MONGODB_USER=admin
MONGODB_PASSWORD=admin123

# Service ports (defaults are fine)
# See .env.template for all available options
```

## Step 2: Start Infrastructure

```bash
# Start all infrastructure services
docker-compose up -d

# Verify services are running
docker-compose ps
```

**Infrastructure Services Started:**
- PostgreSQL (5432)
- MongoDB (27017)
- Redis (6379)
- Kafka + Zookeeper (9092)
- Elasticsearch (9200)
- Zipkin (9411)

Wait about 30 seconds for all services to be healthy.

## Step 3: Build the Project

```bash
# Build all modules (skip tests for faster build)
mvn clean install -DskipTests

# Or use the build script
./scripts/build-all.sh
```

Build time: ~2-5 minutes (first time may take longer for dependency downloads)

## Step 4: Run Services

### Option A: Run All Services (Recommended)

```bash
# Start all services in background
./scripts/run-all-services.sh

# Check service status
./scripts/check-services.sh
```

Logs are written to `logs/` directory:
```bash
tail -f logs/cart-service.log
```

### Option B: Run Services Individually

```bash
# Run infrastructure services first
./scripts/run-service.sh eureka-server
./scripts/run-service.sh api-gateway

# Then run business services
./scripts/run-service.sh user-service
./scripts/run-service.sh product-service
./scripts/run-service.sh cart-service
# ... etc
```

### Option C: Run with Maven

```bash
cd services/cart-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Step 5: Verify Everything is Running

### Check Service Health

```bash
./scripts/check-services.sh
```

Expected output:
```
Checking service health...
✅ Eureka Server (8761): UP
✅ API Gateway (8080): UP
✅ User Service (8081): UP
✅ Product Service (8082): UP
✅ Cart Service (8083): UP
...
```

### Access Key URLs

| Service | URL |
|---------|-----|
| **API Gateway** | http://localhost:8080 |
| **Swagger UI** | http://localhost:8080/swagger-ui.html |
| **Eureka Dashboard** | http://localhost:8761 |
| **Grafana** | http://localhost:3000 (admin/admin) |
| **Zipkin** | http://localhost:9411 |

## Step 6: Test the APIs

### Using Swagger UI

1. Open http://localhost:8080/swagger-ui.html
2. Select a service from the dropdown
3. Try out any endpoint

### Using curl

```bash
# Health check
curl http://localhost:8080/actuator/health

# Get products (no auth required with SECURITY_ENABLED=false)
curl http://localhost:8082/api/products

# Create a cart
curl -X POST http://localhost:8083/api/carts \
  -H "Content-Type: application/json" \
  -d '{"userId": "user123"}'
```

## Stopping Services

```bash
# Stop all services
./scripts/stop-all-services.sh

# Stop infrastructure
docker-compose down

# Stop infrastructure and remove volumes (clean slate)
docker-compose down -v
```

## Troubleshooting

### Port Already in Use

```bash
# Find and kill process on port 8083
lsof -ti:8083 | xargs kill -9
```

### Service Won't Start

1. Check logs: `tail -f logs/<service-name>.log`
2. Verify infrastructure: `docker-compose ps`
3. Check Eureka registration: http://localhost:8761

### Database Connection Issues

```bash
# Restart infrastructure
docker-compose down
docker-compose up -d

# Wait 30 seconds, then restart services
./scripts/run-all-services.sh
```

### Out of Memory

Edit `scripts/run-service.sh` or set:
```bash
export MAVEN_OPTS="-Xmx1024m"
```

## Development Scripts Reference

| Script | Description |
|--------|-------------|
| `./scripts/setup-local-dev.sh` | Setup local development environment |
| `./scripts/build-all.sh` | Build all services |
| `./scripts/run-all-services.sh` | Run all services in background |
| `./scripts/run-service.sh <name>` | Run a single service |
| `./scripts/stop-all-services.sh` | Stop all running services |
| `./scripts/check-services.sh` | Check service health status |

See [scripts/README.md](scripts/README.md) for detailed script documentation.

## Next Steps

1. **Explore the APIs**: Use Swagger UI to explore all available endpoints
2. **Check Monitoring**: View metrics in Grafana (http://localhost:3000)
3. **View Traces**: Analyze request flows in Zipkin (http://localhost:9411)
4. **Run Tests**: `mvn test` to run unit tests
5. **Read Documentation**: Check [docs/](docs/) for detailed guides

## Service Port Reference

| Service | HTTP | gRPC |
|---------|------|------|
| API Gateway | 8080 | - |
| Eureka Server | 8761 | - |
| User Service | 8081 | - |
| Product Service | 8082 | 9091 |
| Cart Service | 8083 | - |
| Order Service | 8084 | - |
| Payment Service | 8085 | - |
| Inventory Service | 8086 | 9092 |
| Notification Service | 8087 | - |
| Search Service | 8088 | - |
| Media Service | 8089 | - |
| Promotion Service | 8090 | 9090 |

---

For more detailed documentation, see [README.md](README.md) and [docs/](docs/).
