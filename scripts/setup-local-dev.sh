#!/bin/bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}E-Commerce Platform - Local Dev Setup${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

# Check if Docker is running
echo -e "${YELLOW}Checking Docker status...${NC}"
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}Error: Docker is not running!${NC}"
    echo ""
    echo "Please start Docker Desktop:"
    echo "  1. Open Docker Desktop application"
    echo "  2. Wait for Docker to start (~30 seconds)"
    echo "  3. Run this script again"
    echo ""
    echo "On macOS, you can start it with:"
    echo "  open -a Docker"
    echo ""
    exit 1
fi
echo -e "${GREEN}✓ Docker is running${NC}"
echo ""

# Check if .env file exists
if [ ! -f .env ]; then
    echo -e "${RED}Error: .env file not found!${NC}"
    echo "Please copy .env.example to .env and configure it"
    exit 1
fi

# Load environment variables
export $(cat .env | grep -v '^#' | xargs)

# Databases are created on first container boot by the init scripts mounted in
# docker-compose.yml (docker/init-postgres.sql, docker/init-mysql.sql); schema
# migrations run via each service's Flyway. No manual psql step needed.
echo -e "${YELLOW}Step 1: Starting infrastructure services${NC}"
docker-compose up -d postgres mysql mongodb redis zookeeper kafka elasticsearch mailhog zipkin eureka-server

echo "Waiting for services to be ready..."
sleep 30

# Check service health
echo ""
echo -e "${YELLOW}Checking service health...${NC}"

# PostgreSQL
if pg_isready -h localhost -p 5432 > /dev/null 2>&1; then
    echo -e "${GREEN}✓ PostgreSQL is ready${NC}"
else
    echo -e "${RED}✗ PostgreSQL is not ready${NC}"
fi

# MySQL
if mysqladmin ping -h localhost --silent 2>/dev/null; then
    echo -e "${GREEN}✓ MySQL is ready${NC}"
else
    echo -e "${RED}✗ MySQL is not ready${NC}"
fi

# Redis
if redis-cli -h localhost ping > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Redis is ready${NC}"
else
    echo -e "${RED}✗ Redis is not ready${NC}"
fi

# MongoDB
if mongosh --eval "db.adminCommand('ping')" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ MongoDB is ready${NC}"
else
    echo -e "${RED}✗ MongoDB is not ready${NC}"
fi

# Elasticsearch
if curl -s http://localhost:9200/_cluster/health > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Elasticsearch is ready${NC}"
else
    echo -e "${RED}✗ Elasticsearch is not ready${NC}"
fi

# Zipkin
if curl -s http://localhost:9411/health > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Zipkin is ready${NC}"
else
    echo -e "${RED}✗ Zipkin is not ready${NC}"
fi

# Eureka
if curl -s http://localhost:8761/actuator/health > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Eureka Server is ready${NC}"
else
    echo -e "${RED}✗ Eureka Server is not ready${NC}"
fi

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Local development environment is ready!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo -e "${BLUE}Infrastructure Services:${NC}"
echo "  - PostgreSQL: localhost:5432"
echo "  - MySQL: localhost:3306"
echo "  - MongoDB: localhost:27017"
echo "  - Redis: localhost:6379"
echo "  - Kafka: localhost:9092"
echo "  - Elasticsearch: http://localhost:9200"
echo "  - MailHog: SMTP localhost:1025, UI http://localhost:8025"
echo "  - Zipkin: http://localhost:9411"
echo "  - Eureka: http://localhost:8761"
echo ""
echo -e "${BLUE}Next Steps:${NC}"
echo "  1. Build the project: ./scripts/build-all.sh"
echo "  2. Run services: ./scripts/run-service.sh <service-name>"
echo "  3. Or run all: ./scripts/run-all-services.sh"
echo ""
