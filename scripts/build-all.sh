#!/bin/bash

##############################################################################
# Build All Services
#
# This script builds all microservices in the project.
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

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Building E-Commerce Platform${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

echo -e "${YELLOW}Running Maven clean install...${NC}"
echo ""

# Build with Maven
mvn clean install -DskipTests

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Build completed successfully!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo -e "${BLUE}Next steps:${NC}"
echo "  1. Setup local environment: ./scripts/setup-local-dev.sh"
echo "  2. Run services: ./scripts/run-all-services.sh"
echo ""
