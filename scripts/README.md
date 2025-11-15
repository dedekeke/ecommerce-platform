# Development Scripts

This directory contains shell scripts to help with local development of the e-commerce platform.

## Scripts Overview

| Script | Description | Usage |
|--------|-------------|-------|
| `setup-local-dev.sh` | Setup local development environment | `./setup-local-dev.sh` |
| `build-all.sh` | Build all services | `./build-all.sh` |
| `run-service.sh` | Run a single service | `./run-service.sh <service-name>` |
| `run-all-services.sh` | Run all services | `./run-all-services.sh` |
| `stop-all-services.sh` | Stop all running services | `./stop-all-services.sh` |

## Quick Start

### 1. Initial Setup

```bash
# Clone the repository
git clone <repository-url>
cd ecommerce-platform

# Copy environment variables
cp .env.example .env
# Edit .env with your configuration

# Setup local development environment
./scripts/setup-local-dev.sh
```

This will:
- Start PostgreSQL, MySQL, MongoDB, Redis, and Kafka
- Create required databases
- Verify service health

### 2. Build the Project

```bash
./scripts/build-all.sh
```

This compiles all services and installs dependencies.

### 3. Run Services

#### Option A: Run All Services (with tmux)

```bash
./scripts/run-all-services.sh
```

If you have tmux installed, this will:
- Start all services in separate tmux windows
- Attach to the tmux session
- Allow easy switching between service logs

**tmux Commands:**
- Switch windows: `Ctrl+B` then `0-5` or `n`/`p`
- Detach: `Ctrl+B` then `d`
- Reattach: `tmux attach -t ecommerce-services`
- Kill session: `tmux kill-session -t ecommerce-services`

#### Option B: Run All Services (background mode)

If tmux is not installed, services will run in background with logs in `logs/` directory:

```bash
./scripts/run-all-services.sh

# View logs
tail -f logs/cart-service.log
```

#### Option C: Run Individual Service

```bash
./scripts/run-service.sh cart-service
```

### 4. Stop Services

```bash
./scripts/stop-all-services.sh
```

## Available Services

| Service | Port | Script Name |
|---------|------|-------------|
| Eureka Server | 8761 | `eureka-server` |
| Config Server | 8888 | `config-server` |
| User Service | 8088 | `user-service` |
| Product Service | 8082 | `product-service` |
| Cart Service | 8083 | `cart-service` |
| API Gateway | 8080 | `api-gateway` |

## Detailed Script Documentation

### setup-local-dev.sh

Sets up the local development environment by starting infrastructure services.

**Prerequisites:**
- Docker and docker-compose installed
- `.env` file configured

**What it does:**
1. Checks for required `.env` file
2. Creates cart_db database in PostgreSQL
3. Starts infrastructure services (PostgreSQL, MySQL, MongoDB, Redis, Kafka)
4. Verifies service health
5. Displays connection information

**Example:**
```bash
./scripts/setup-local-dev.sh
```

### build-all.sh

Builds all microservices using Maven.

**What it does:**
1. Runs `mvn clean install -DskipTests`
2. Compiles all services
3. Installs artifacts to local Maven repository

**Example:**
```bash
./scripts/build-all.sh
```

**Options:**
```bash
# Build with tests
mvn clean install

# Build specific service
mvn clean install -pl services/cart-service -am
```

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

### run-all-services.sh

Runs all microservices either in tmux or background mode.

**Usage:**
```bash
./scripts/run-all-services.sh
```

**What it does:**

**With tmux:**
1. Creates a new tmux session named "ecommerce-services"
2. Creates a window for each service
3. Starts services in order with 2-second delays
4. Attaches to the session

**Without tmux:**
1. Starts each service in background
2. Redirects output to `logs/<service>.log`
3. Saves PID to `logs/<service>.pid`
4. Delays 5 seconds between services

**Service Startup Order:**
1. Eureka Server (8761)
2. Config Server (8888)
3. User Service (8088)
4. Product Service (8082)
5. Cart Service (8083)
6. API Gateway (8080)

### stop-all-services.sh

Stops all running microservices.

**Usage:**
```bash
./scripts/stop-all-services.sh
```

**What it does:**
1. Kills tmux session if running
2. Stops processes using PID files in `logs/` directory
3. Kills remaining Spring Boot processes
4. Cleans up PID files

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
```

### Service Won't Stop

**Problem:** `stop-all-services.sh` doesn't stop all services

**Solution:**
```bash
# Force kill all Maven processes
pkill -9 -f maven

# Force kill all Java processes
pkill -9 -f "spring-boot:run"

# Manually kill tmux session
tmux kill-session -t ecommerce-services
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

## Log Files

When running services in background mode, logs are stored in:

```
logs/
├── cart-service.log
├── cart-service.pid
├── user-service.log
├── user-service.pid
├── product-service.log
├── product-service.pid
├── eureka-server.log
└── eureka-server.pid
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

## Additional Resources

- [Main README](../README.md) - Project overview
- [IntelliJ IDEA Guide](../docs/IDEA_RUN_CONFIGURATIONS.md) - IDE setup
- [Cart Service README](../services/cart-service/README.md) - Service documentation
- [Architecture Docs](../docs/ARCHITECTURE.md) - System architecture

## Tips

1. **Use tmux** for better service management (install with `brew install tmux`)
2. **Run Eureka first** to avoid service registration delays
3. **Check logs** in `logs/` directory if services don't start
4. **Wait 30 seconds** after starting services for full initialization
5. **Set SECURITY_ENABLED=false** for easier local development without Auth0
6. **Use Swagger UI** to test APIs without authentication
7. **Monitor Eureka dashboard** to verify service registration
