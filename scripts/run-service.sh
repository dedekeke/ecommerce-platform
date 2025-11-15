#!/bin/bash

##############################################################################
# Run Individual Service
#
# Usage: ./run-service.sh <service-name>
# Example: ./run-service.sh cart-service
##############################################################################

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

SERVICE=$1

if [ -z "$SERVICE" ]; then
    echo -e "${RED}Error: Service name is required${NC}"
    echo ""
    echo "Usage: $0 <service-name>"
    echo ""
    echo "Available services:"
    echo "  - user-service"
    echo "  - product-service"
    echo "  - cart-service"
    echo "  - eureka-server"
    echo "  - config-server"
    echo "  - api-gateway"
    exit 1
fi

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

# Load environment variables
if [ -f .env ]; then
    export $(cat .env | grep -v '^#' | xargs)
fi

# Determine service path and profile
case "$SERVICE" in
    user-service|product-service|cart-service)
        SERVICE_PATH="services/$SERVICE"
        PROFILE="local"
        ;;
    eureka-server|config-server|api-gateway)
        SERVICE_PATH="infrastructure/$SERVICE"
        PROFILE="local"
        ;;
    *)
        echo -e "${RED}Error: Unknown service '$SERVICE'${NC}"
        exit 1
        ;;
esac

if [ ! -d "$SERVICE_PATH" ]; then
    echo -e "${RED}Error: Service directory not found: $SERVICE_PATH${NC}"
    exit 1
fi

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Running $SERVICE${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Create logs directory
mkdir -p logs

# Run the service
echo -e "${YELLOW}Starting $SERVICE with profile: $PROFILE${NC}"
echo ""

mvn spring-boot:run \
    -pl "$SERVICE_PATH" \
    -Dspring-boot.run.profiles="$PROFILE" \
    -Dspring-boot.run.jvmArguments="-Xmx512m -Xms256m" \
    2>&1 | tee "logs/$SERVICE.log"
