#!/bin/bash

##############################################################################
# Build Script for E-Commerce Platform
#
# Usage:
#   ./build-all.sh              - Build only (skip tests)
#   ./build-all.sh --test       - Build with tests
#   ./build-all.sh --deploy     - Build and start with Docker
##############################################################################

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

# Parse arguments
RUN_TESTS=false
DEPLOY=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --test)
            RUN_TESTS=true
            shift
            ;;
        --deploy)
            DEPLOY=true
            shift
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Usage: $0 [--test] [--deploy]"
            exit 1
            ;;
    esac
done

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Building E-Commerce Platform${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Check .env file for deployment
if [ "$DEPLOY" = true ] && [ ! -f .env ]; then
    echo -e "${RED}Error: .env file not found!${NC}"
    echo "Creating .env from .env.example..."
    if [ -f .env.example ]; then
        cp .env.example .env
        echo -e "${GREEN}Created .env file. Please edit it with your Auth0 credentials.${NC}"
        exit 1
    else
        echo -e "${RED}Error: .env.example not found. Please create .env manually.${NC}"
        exit 1
    fi
fi

# Build with Maven
if [ "$RUN_TESTS" = true ]; then
    echo -e "${YELLOW}Running Maven clean install with tests...${NC}"
    mvn clean install
else
    echo -e "${YELLOW}Running Maven clean install (skipping tests)...${NC}"
    mvn clean install -DskipTests
fi

if [ $? -ne 0 ]; then
    echo -e "${RED}Maven build failed!${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Build completed successfully!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

# Deploy if requested
if [ "$DEPLOY" = true ]; then
    echo -e "${YELLOW}Starting Docker containers...${NC}"
    docker-compose up -d

    if [ $? -ne 0 ]; then
        echo -e "${RED}Docker Compose failed!${NC}"
        exit 1
    fi

    echo ""
    echo -e "${GREEN}All services started!${NC}"
    echo ""
    echo -e "${BLUE}============================================${NC}"
    echo -e "${BLUE}Service URLs:${NC}"
    echo -e "${BLUE}============================================${NC}"
    echo "Eureka Server:       http://localhost:8761"
    echo "Config Server:       http://localhost:8888"
    echo "API Gateway:         http://localhost:8080"
    echo "  Swagger UI:        http://localhost:8080/swagger-ui.html"
    echo ""
    echo "Microservices:"
    echo "  User Service:      http://localhost:8081/swagger-ui.html"
    echo "  Product Service:   http://localhost:8082/swagger-ui.html"
    echo "  Cart Service:      http://localhost:8083/swagger-ui.html"
    echo "  Order Service:     http://localhost:8084/swagger-ui.html"
    echo "  Payment Service:   http://localhost:8085/swagger-ui.html"
    echo "  Inventory Service: http://localhost:8086/swagger-ui.html"
    echo "  Notification:      http://localhost:8087/swagger-ui.html"
    echo "  Search Service:    http://localhost:8089/swagger-ui.html"
    echo "  Media Service:     http://localhost:8090/swagger-ui.html"
    echo "  Promotion Service: http://localhost:8091/swagger-ui.html"
    echo ""
    echo "Monitoring:"
    echo "  Zipkin:            http://localhost:9411"
    echo "  Grafana:           http://localhost:3000 (admin/admin)"
    echo "  Prometheus:        http://localhost:9090"
    echo ""
    echo -e "${BLUE}============================================${NC}"
    echo ""
    echo "To check services:   ./scripts/check-services.sh"
    echo "To view logs:        docker-compose logs -f [service-name]"
    echo "To stop:             docker-compose down"
else
    echo -e "${BLUE}Next steps:${NC}"
    echo "  1. Setup local environment: ./scripts/setup-local-dev.sh"
    echo "  2. Run services: ./scripts/run-all-services.sh"
    echo "  Or deploy with Docker: ./scripts/build-all.sh --deploy"
fi
echo ""
