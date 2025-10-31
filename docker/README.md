# Docker Infrastructure Setup

## Overview

This directory contains Docker Compose configurations and initialization scripts for the E-Commerce Platform infrastructure.

## Database Architecture

### PostgreSQL (Transactional Services)
**Services:** User, Order, Payment, Inventory
**Why:** Strong ACID compliance, complex queries, financial data

**Databases:**
- `userdb` - User management and profiles
- `orderdb` - Order processing and management
- `paymentdb` - Payment processing and transactions
- `inventorydb` - Stock management and reservations

### MySQL (Read-Heavy Services)
**Services:** Product, Promotion
**Why:** Optimized for read-heavy workloads, simple queries, fast reads

**Databases:**
- `productdb` - Product catalog management
- `promotiondb` - Discounts and promotions

### MongoDB (Document Storage)
**Services:** Cart, Notification, Media
**Collections:** Created automatically by services

## Files

- `init-postgres.sql` - PostgreSQL database initialization script
- `init-mysql.sql` - MySQL database initialization script

## Starting the Infrastructure

### 1. Start Core Infrastructure (Databases + Kafka)

```bash
docker-compose -f docker-compose.yml up -d
```

This starts:
- PostgreSQL (port 5432)
- MySQL (port 3306)
- MongoDB (port 27017)
- Redis (port 6379)
- Kafka (port 9092)
- Zookeeper (port 2181)

### 2. Start Monitoring Stack (Optional)

```bash
docker-compose -f docker-compose.monitoring.yml up -d
```

This starts:
- Zipkin (port 9411) - Distributed tracing
- Prometheus (port 9090) - Metrics collection
- Grafana (port 3000) - Visualization (admin/admin)
- Kafka UI (port 8080) - Kafka management

### 3. Verify Services

```bash
# Check all services are running
docker-compose ps

# Check service logs
docker-compose logs -f [service-name]

# Health checks
curl http://localhost:9411/health  # Zipkin
curl http://localhost:9090/-/healthy  # Prometheus
curl http://localhost:3000/api/health  # Grafana
```

## Service Endpoints

| Service | Port | Credentials | UI |
|---------|------|-------------|-----|
| PostgreSQL | 5432 | admin/admin123 | - |
| MySQL | 3306 | admin/admin123 | - |
| MongoDB | 27017 | admin/admin123 | - |
| Redis | 6379 | - | - |
| Kafka | 9092 | - | http://localhost:8080 |
| Zipkin | 9411 | - | http://localhost:9411 |
| Prometheus | 9090 | - | http://localhost:9090 |
| Grafana | 3000 | admin/admin | http://localhost:3000 |

## Connecting to Databases

### PostgreSQL
```bash
docker exec -it ecommerce-postgres psql -U admin -d userdb
```

### MySQL
```bash
docker exec -it ecommerce-mysql mysql -uadmin -padmin123
```

### MongoDB
```bash
docker exec -it ecommerce-mongodb mongosh -u admin -p admin123 --authenticationDatabase admin
```

## Stopping Services

```bash
# Stop all services
docker-compose down

# Stop and remove volumes (WARNING: This deletes all data!)
docker-compose down -v
```

## Network

All services run on the `ecommerce-network` bridge network with subnet `172.20.0.0/16`.

## Data Persistence

Data is persisted in Docker volumes:
- `postgres-data`
- `mysql-data`
- `mongodb-data`
- `redis-data`
- `kafka-data`
- `zookeeper-data`
- `prometheus-data`
- `grafana-data`

## Troubleshooting

### Services not starting
```bash
# Check Docker daemon is running
docker info

# Check logs for specific service
docker-compose logs [service-name]

# Restart a specific service
docker-compose restart [service-name]
```

### Database connection issues
```bash
# Verify database is accepting connections
docker-compose ps

# Check health status
docker inspect --format='{{json .State.Health}}' [container-name]
```

### Port conflicts
If ports are already in use, modify the port mappings in `docker-compose.yml`:
```yaml
ports:
  - "NEW_PORT:CONTAINER_PORT"
```

## Next Steps

After infrastructure is running:
1. Verify all health checks pass
2. Access Grafana and verify Prometheus datasource
3. Proceed with Day 3: Service Discovery and Config Server
