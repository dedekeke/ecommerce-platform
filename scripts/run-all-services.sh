#!/bin/bash

##############################################################################
# Run All Services Locally
#
# This script starts all microservices in separate terminal windows/tabs.
# Requires tmux or uses background processes with log files.
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

# Load environment variables
if [ -f .env ]; then
    export $(cat .env | grep -v '^#' | xargs)
fi

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}E-Commerce Platform - Run All Services${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Create logs directory
mkdir -p logs

# Services to run (in order)
SERVICES=(
    "eureka-server:8761"
    "config-server:8888"
    "user-service:8081"
    "product-service:8082"
    "cart-service:8083"
    "order-service:8084"
    "payment-service:8085"
    "inventory-service:8086"
    "api-gateway:8080"
)

# Run services in background
for SERVICE_INFO in "${SERVICES[@]}"; do
    SERVICE="${SERVICE_INFO%%:*}"
    PORT="${SERVICE_INFO##*:}"

    echo -e "${YELLOW}Starting $SERVICE on port $PORT...${NC}"

    # Determine service path
    case "$SERVICE" in
        user-service|product-service|cart-service|order-service|payment-service|inventory-service)
            SERVICE_PATH="services/$SERVICE"
            ;;
        eureka-server|config-server|api-gateway)
            SERVICE_PATH="infrastructure/$SERVICE"
            ;;
    esac

    # Start service in background
    nohup mvn spring-boot:run \
        -pl "$SERVICE_PATH" \
        -Dspring-boot.run.profiles=local \
        -Dspring-boot.run.jvmArguments="-Xmx512m -Xms256m" \
        > "logs/$SERVICE.log" 2>&1 &

    echo $! > "logs/$SERVICE.pid"
    echo -e "${GREEN}✓ Started $SERVICE (PID: $(cat logs/$SERVICE.pid))${NC}"

    # Wait a bit before starting next service
    sleep 5
done

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}All services started in background${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo -e "${BLUE}Service logs are in: logs/<service-name>.log${NC}"
echo ""
echo -e "${BLUE}To view logs:${NC}"
echo "  tail -f logs/<service-name>.log"
echo ""
echo -e "${BLUE}To stop all services:${NC}"
echo "  ./scripts/stop-all-services.sh"
echo ""


echo -e "${BLUE}Service URLs:${NC}"
echo "  - Eureka Dashboard:  http://localhost:8761"
echo "  - API Gateway:       http://localhost:8080"
echo "  - User Service:      http://localhost:8081/swagger-ui.html"
echo "  - Product Service:   http://localhost:8082/swagger-ui.html"
echo "  - Cart Service:      http://localhost:8083/swagger-ui.html"
echo "  - Order Service:     http://localhost:8084/swagger-ui.html"
echo "  - Payment Service:   http://localhost:8085/swagger-ui.html"
echo "  - Inventory Service: http://localhost:8086/swagger-ui.html"
echo ""
