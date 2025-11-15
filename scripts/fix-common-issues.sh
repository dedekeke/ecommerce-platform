#!/bin/bash

##############################################################################
# Fix Common Issues Script
#
# Automatically fixes common startup issues:
# - Docker not running
# - Missing databases
# - Missing config-repo commits
##############################################################################

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Fixing Common Issues${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

# Issue 1: Check Docker
echo -e "${YELLOW}[1/5] Checking Docker status...${NC}"
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}✗ Docker is not running${NC}"
    echo ""
    echo "Starting Docker Desktop..."
    open -a Docker

    echo "Waiting for Docker to start..."
    for i in {1..30}; do
        if docker info > /dev/null 2>&1; then
            echo -e "${GREEN}✓ Docker started successfully${NC}"
            break
        fi
        echo -n "."
        sleep 2
    done
    echo ""

    if ! docker info > /dev/null 2>&1; then
        echo -e "${RED}Failed to start Docker automatically${NC}"
        echo "Please start Docker Desktop manually and run this script again"
        exit 1
    fi
else
    echo -e "${GREEN}✓ Docker is running${NC}"
fi
echo ""

# Issue 2: Start infrastructure
echo -e "${YELLOW}[2/5] Starting infrastructure services...${NC}"
docker-compose up -d postgres mysql redis kafka zookeeper mongodb

echo "Waiting for services to be ready..."
sleep 15
echo -e "${GREEN}✓ Infrastructure services started${NC}"
echo ""

# Issue 3: Create databases
echo -e "${YELLOW}[3/5] Creating databases...${NC}"

# PostgreSQL databases
echo "Creating PostgreSQL databases..."
PGPASSWORD=postgres psql -h localhost -U postgres -tc "SELECT 1 FROM pg_database WHERE datname = 'cart_db'" | grep -q 1 || \
PGPASSWORD=postgres psql -h localhost -U postgres -c "CREATE DATABASE cart_db;"
echo -e "${GREEN}  ✓ cart_db${NC}"

PGPASSWORD=postgres psql -h localhost -U postgres -tc "SELECT 1 FROM pg_database WHERE datname = 'userdb'" | grep -q 1 || \
PGPASSWORD=postgres psql -h localhost -U postgres -c "CREATE DATABASE userdb;"
echo -e "${GREEN}  ✓ userdb${NC}"

# MySQL databases
echo "Creating MySQL databases..."
mysql -h localhost -u admin -padmin123 -e "CREATE DATABASE IF NOT EXISTS productdb;"
echo -e "${GREEN}  ✓ productdb${NC}"

echo ""

# Issue 4: Fix config-repo
echo -e "${YELLOW}[4/5] Checking config-repo...${NC}"
cd "$PROJECT_ROOT/config-repo"

# Check if it's a git repo
if [ ! -d .git ]; then
    echo "Initializing git repository..."
    git init
    git add .
    git commit -m "Initial commit"
    echo -e "${GREEN}✓ Config repo initialized${NC}"
else
    # Check for uncommitted changes
    if [[ -n $(git status -s) ]]; then
        echo "Committing configuration changes..."
        git add .
        git commit -m "Update service configurations" || true
        echo -e "${GREEN}✓ Config changes committed${NC}"
    else
        echo -e "${GREEN}✓ Config repo is up to date${NC}"
    fi
fi

cd "$PROJECT_ROOT"
echo ""

# Issue 5: Verify everything
echo -e "${YELLOW}[5/5] Verifying setup...${NC}"

# Check PostgreSQL
if pg_isready -h localhost -p 5432 > /dev/null 2>&1; then
    echo -e "${GREEN}✓ PostgreSQL is ready${NC}"
else
    echo -e "${RED}✗ PostgreSQL is not ready${NC}"
fi

# Check MySQL
if mysqladmin ping -h localhost --silent 2>/dev/null; then
    echo -e "${GREEN}✓ MySQL is ready${NC}"
else
    echo -e "${RED}✗ MySQL is not ready${NC}"
fi

# Check Redis
if redis-cli -h localhost ping > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Redis is ready${NC}"
else
    echo -e "${RED}✗ Redis is not ready${NC}"
fi

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Common issues fixed!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo -e "${BLUE}Next steps:${NC}"
echo "  1. Build services: ./scripts/build-all.sh"
echo "  2. Run services: ./scripts/run-all-services.sh"
echo ""
echo -e "${BLUE}Service URLs after starting:${NC}"
echo "  - Product Service: http://localhost:8082/swagger-ui.html"
echo "  - Cart Service: http://localhost:8083/swagger-ui.html"
echo "  - User Service: http://localhost:8088/swagger-ui.html"
echo ""
