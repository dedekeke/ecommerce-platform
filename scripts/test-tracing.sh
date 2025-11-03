#!/bin/bash

# Distributed Tracing Test Script
# This script tests the tracing infrastructure by making requests and verifying traces appear in Zipkin

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
ZIPKIN_URL="${ZIPKIN_URL:-http://localhost:9411}"
API_GATEWAY_URL="${API_GATEWAY_URL:-http://localhost:8080}"
EUREKA_URL="${EUREKA_URL:-http://localhost:8761}"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Distributed Tracing Test Script${NC}"
echo -e "${BLUE}========================================${NC}\n"

# Function to print colored messages
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check if a service is running
check_service() {
    local service_name=$1
    local service_url=$2

    print_info "Checking $service_name..."
    if curl -s -f "$service_url/actuator/health" > /dev/null 2>&1; then
        print_success "$service_name is running"
        return 0
    else
        print_error "$service_name is not running at $service_url"
        return 1
    fi
}

# Function to wait for a service to be ready
wait_for_service() {
    local service_name=$1
    local service_url=$2
    local max_attempts=30
    local attempt=1

    print_info "Waiting for $service_name to be ready..."

    while [ $attempt -le $max_attempts ]; do
        if curl -s -f "$service_url/actuator/health" > /dev/null 2>&1; then
            print_success "$service_name is ready"
            return 0
        fi

        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done

    print_error "$service_name failed to start within timeout"
    return 1
}

# Step 1: Check Zipkin
echo -e "\n${BLUE}Step 1: Checking Zipkin${NC}"
if ! check_service "Zipkin" "$ZIPKIN_URL"; then
    print_error "Zipkin is not running. Please start it with: docker-compose up -d zipkin"
    exit 1
fi

# Verify Zipkin API
print_info "Testing Zipkin API..."
zipkin_health=$(curl -s "$ZIPKIN_URL/health" || echo "failed")
if [[ "$zipkin_health" == *"UP"* ]] || [[ "$zipkin_health" == *"ok"* ]]; then
    print_success "Zipkin API is healthy"
else
    print_warning "Zipkin health check returned: $zipkin_health"
fi

# Step 2: Check Infrastructure Services
echo -e "\n${BLUE}Step 2: Checking Infrastructure Services${NC}"

if check_service "Eureka Server" "$EUREKA_URL"; then
    # Get registered services
    print_info "Fetching registered services from Eureka..."
    registered_services=$(curl -s "$EUREKA_URL/eureka/apps" -H "Accept: application/json" | jq -r '.applications.application[].name' 2>/dev/null || echo "")
    if [ -n "$registered_services" ]; then
        print_success "Registered services:"
        echo "$registered_services" | while read -r service; do
            echo "  - $service"
        done
    fi
fi

check_service "Config Server" "http://localhost:8888" || print_warning "Config Server is not running"
check_service "API Gateway" "$API_GATEWAY_URL" || print_warning "API Gateway is not running"

# Step 3: Generate Test Traces
echo -e "\n${BLUE}Step 3: Generating Test Traces${NC}"

print_info "Generating traces by making requests to services..."

# Generate a correlation ID for tracing
CORRELATION_ID="test-trace-$(date +%s)"
print_info "Using correlation ID: $CORRELATION_ID"

# Test 1: Health check request (should create a trace)
print_info "Test 1: Making health check request to API Gateway..."
response=$(curl -s -w "\n%{http_code}" \
    -H "X-Correlation-ID: $CORRELATION_ID" \
    "$API_GATEWAY_URL/actuator/health" 2>/dev/null || echo "000")

http_code=$(echo "$response" | tail -n 1)
if [ "$http_code" = "200" ]; then
    print_success "Health check request successful (HTTP $http_code)"
else
    print_warning "Health check returned HTTP $http_code"
fi

# Wait for trace to be sent to Zipkin
sleep 2

# Step 4: Verify Traces in Zipkin
echo -e "\n${BLUE}Step 4: Verifying Traces in Zipkin${NC}"

print_info "Querying Zipkin for traces..."

# Get traces from the last minute
end_ts=$(date +%s)000  # milliseconds
start_ts=$((end_ts - 60000))  # 1 minute ago

traces_response=$(curl -s "$ZIPKIN_URL/api/v2/traces?endTs=$end_ts&lookback=60000&limit=100")

if [ $? -eq 0 ]; then
    trace_count=$(echo "$traces_response" | jq '. | length' 2>/dev/null || echo "0")
    print_success "Found $trace_count traces in the last minute"

    if [ "$trace_count" -gt 0 ]; then
        print_info "Recent trace details:"
        echo "$traces_response" | jq -r '.[0] | "  Trace ID: \(.[0].traceId)\n  Duration: \((.[0].duration / 1000))ms\n  Services: \([.[].localEndpoint.serviceName] | unique | join(", "))"' 2>/dev/null || echo "  Unable to parse trace details"
    fi
else
    print_error "Failed to query Zipkin API"
fi

# Step 5: Check Service Metrics
echo -e "\n${BLUE}Step 5: Checking Service Metrics${NC}"

print_info "Checking tracing metrics from API Gateway..."
metrics=$(curl -s "$API_GATEWAY_URL/actuator/metrics/http.server.requests" | jq '.measurements[0].value' 2>/dev/null || echo "N/A")
if [ "$metrics" != "N/A" ]; then
    print_success "HTTP request metrics available: $metrics requests"
else
    print_warning "Unable to fetch metrics"
fi

# Step 6: Test Correlation ID Propagation
echo -e "\n${BLUE}Step 6: Testing Correlation ID Propagation${NC}"

print_info "Making request with custom correlation ID..."
CUSTOM_CORRELATION_ID="correlation-test-$(uuidgen 2>/dev/null || echo 'test-123')"

response_headers=$(curl -s -i -H "X-Correlation-ID: $CUSTOM_CORRELATION_ID" "$API_GATEWAY_URL/actuator/health" 2>/dev/null)
returned_correlation=$(echo "$response_headers" | grep -i "X-Correlation-ID:" | cut -d' ' -f2 | tr -d '\r')

if [ "$returned_correlation" = "$CUSTOM_CORRELATION_ID" ]; then
    print_success "Correlation ID properly propagated in response"
else
    print_warning "Correlation ID in response: $returned_correlation (expected: $CUSTOM_CORRELATION_ID)"
fi

# Step 7: Summary
echo -e "\n${BLUE}========================================${NC}"
echo -e "${BLUE}Tracing Test Summary${NC}"
echo -e "${BLUE}========================================${NC}\n"

print_info "Zipkin UI: $ZIPKIN_URL"
print_info "To view traces, search for:"
print_info "  - Correlation ID: $CORRELATION_ID"
print_info "  - Service name: api-gateway"
print_info "  - Time range: last 5 minutes"

echo -e "\n${GREEN}Test completed!${NC}"
echo -e "\nNext steps:"
echo "1. Open Zipkin UI: $ZIPKIN_URL"
echo "2. Click 'Run Query' to see all recent traces"
echo "3. Filter by service name to see service-specific traces"
echo "4. Click on a trace to see the complete request flow"

# Optional: Open Zipkin in browser (macOS)
if [[ "$OSTYPE" == "darwin"* ]]; then
    read -p "Do you want to open Zipkin UI in your browser? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        open "$ZIPKIN_URL"
    fi
fi
