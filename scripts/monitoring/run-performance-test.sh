#!/bin/bash

# Performance Test Script for Virtual Threads
# Compares virtual threads vs platform threads performance

set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

# Paths below are repo-root relative; make the script location-independent.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/../.."

API_URL=${1:-"http://localhost:8080"}
OUTPUT_DIR="./performance-results"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULT_FILE="${OUTPUT_DIR}/results_${TIMESTAMP}.txt"

mkdir -p "$OUTPUT_DIR"

echo -e "${GREEN}╔════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  Virtual Threads Performance Test         ║${NC}"
echo -e "${GREEN}╔════════════════════════════════════════════╗${NC}"
echo ""
echo -e "${YELLOW}API URL:${NC} $API_URL"
echo -e "${YELLOW}Results will be saved to:${NC} $RESULT_FILE"
echo ""

# Check if API is accessible
echo -e "${YELLOW}Checking API availability...${NC}"
if curl -f -s "${API_URL}/actuator/health" > /dev/null; then
    echo -e "${GREEN}✓ API is accessible${NC}"
else
    echo -e "${RED}✗ API is not accessible at ${API_URL}${NC}"
    echo -e "${YELLOW}Please ensure services are running:${NC}"
    echo "  docker-compose up -d"
    exit 1
fi
echo ""

# System info
echo -e "${BLUE}═══ System Information ═══${NC}"
echo "Java Version:"
java -version 2>&1 | head -1
echo ""
echo "Available Processors: $(nproc 2>/dev/null || sysctl -n hw.ncpu)"
echo "Total Memory: $(free -h 2>/dev/null | grep Mem | awk '{print $2}' || sysctl -n hw.memsize | awk '{print $1/1024/1024/1024 " GB"}')"
echo ""

# Compile the performance test
echo -e "${YELLOW}Compiling performance test...${NC}"
javac -d /tmp/perf-test \
    -cp "$(find ~/.m2/repository -name 'slf4j-api-*.jar' | head -1):$(find ~/.m2/repository -name 'logback-classic-*.jar' | head -1)" \
    performance-tests/VirtualThreadsPerformanceTest.java 2>&1 | tee -a "$RESULT_FILE"

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Compilation successful${NC}"
else
    echo -e "${RED}✗ Compilation failed${NC}"
    exit 1
fi
echo ""

# Run the test
echo -e "${YELLOW}Running performance tests...${NC}"
echo -e "${YELLOW}This may take several minutes...${NC}"
echo ""

API_URL="$API_URL" java \
    -cp "/tmp/perf-test:$(find ~/.m2/repository -name 'slf4j-api-*.jar' | head -1):$(find ~/.m2/repository -name 'logback-classic-*.jar' | head -1)" \
    com.ecommerce.performance.VirtualThreadsPerformanceTest 2>&1 | tee -a "$RESULT_FILE"

echo ""
echo -e "${GREEN}╔════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  Test Complete                             ║${NC}"
echo -e "${GREEN}╔════════════════════════════════════════════╗${NC}"
echo ""
echo -e "${YELLOW}Results saved to:${NC} $RESULT_FILE"
echo ""

# Extract key metrics
echo -e "${BLUE}═══ Key Findings ═══${NC}"
grep -A 20 "PERFORMANCE COMPARISON" "$RESULT_FILE" | tail -20 || echo "No comparison data found"
echo ""

# Memory comparison (if available)
echo -e "${BLUE}═══ Memory Usage Analysis ═══${NC}"
echo "Check Docker stats during test:"
echo "  docker stats --no-stream"
echo ""

echo -e "${GREEN}Done!${NC}"
