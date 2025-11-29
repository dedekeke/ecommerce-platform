# Quick Start Guide

Get up and running with the e-commerce platform in 5 minutes.

## Prerequisites Checklist

```bash
# Verify you have required software
java --version        # Should be 21+
mvn --version         # Should be 3.9+
docker --version      # Should be 24+
docker-compose --version
```

## 3-Step Setup

### 1. Setup Infrastructure (2 minutes)

```bash
./scripts/setup-local-dev.sh
```

This starts PostgreSQL, MySQL, MongoDB, Redis, and Kafka.

### 2. Build Project (2 minutes)

```bash
./scripts/build-all.sh
```

This compiles all microservices.

### 3. Run Services (1 minute)

**Option A: All services with tmux**
```bash
./scripts/run-all-services.sh
```

**Option B: Single service**
```bash
./scripts/run-service.sh cart-service
```

## Verify It Works

```bash
# Check Eureka Dashboard
open http://localhost:8761

# Check Cart Service
curl http://localhost:8083/actuator/health

# Check Swagger UI
open http://localhost:8083/swagger-ui.html
```

## Test the Cart API

```bash
# Get cart
curl http://localhost:8083/api/cart

# Add item to cart (requires Product Service)
curl -X POST http://localhost:8083/api/cart/items \
  -H "Content-Type: application/json" \
  -d '{"productId": 1, "quantity": 2}'
```

## Service URLs

| Service | URL | Swagger |
|---------|-----|---------|
| Cart Service | http://localhost:8083 | http://localhost:8083/swagger-ui.html |
| User Service | http://localhost:8088 | http://localhost:8088/swagger-ui.html |
| Product Service | http://localhost:8082 | http://localhost:8082/swagger-ui.html |
| Eureka Dashboard | http://localhost:8761 | - |
| API Gateway | http://localhost:8080 | - |

## Stop Services

```bash
./scripts/stop-all-services.sh
```

## Troubleshooting

### Port Already in Use
```bash
lsof -ti:8083 | xargs kill -9
```

### Database Connection Failed
```bash
docker-compose restart postgres
./scripts/setup-local-dev.sh
```

### 401 Unauthorized
Security is disabled by default for local dev. If you see 401 errors:
```bash
# Verify in .env file
SECURITY_ENABLED=false
```

## IntelliJ IDEA Setup

1. **Open Project:** File → Open → Select `pom.xml`
2. **Create Run Configuration:**
   - Run → Edit Configurations → + → Spring Boot
   - Main class: `CartServiceApplication`
   - Active profiles: `local`
   - VM options: `-Xmx512m -Xms256m -DSECURITY_ENABLED=false`

Full guide: [IDEA_RUN_CONFIGURATIONS.md](docs/IDEA_RUN_CONFIGURATIONS.md)

## Need More Help?

- **Complete Setup:** [LOCAL_DEVELOPMENT_GUIDE.md](docs/LOCAL_DEVELOPMENT_GUIDE.md)
- **IntelliJ Guide:** [IDEA_RUN_CONFIGURATIONS.md](docs/IDEA_RUN_CONFIGURATIONS.md)
- **Scripts Help:** [scripts/README.md](scripts/README.md)
- **Cart Service:** [services/cart-service/README.md](services/cart-service/README.md)

## Common Commands

```bash
# Start infrastructure
./scripts/setup-local-dev.sh

# Build all services
./scripts/build-all.sh

# Run specific service
./scripts/run-service.sh cart-service

# Run all services
./scripts/run-all-services.sh

# Stop all services
./scripts/stop-all-services.sh

# View logs
tail -f logs/cart-service.log

# Check service health
curl http://localhost:8083/actuator/health

# Test API
open http://localhost:8083/swagger-ui.html
```

## Development Workflow

```bash
# Morning: Start infrastructure
./scripts/setup-local-dev.sh

# After git pull: Rebuild
./scripts/build-all.sh

# During development: Run service
./scripts/run-service.sh cart-service

# Make changes... test with Swagger UI

# Evening: Stop services
./scripts/stop-all-services.sh
```
