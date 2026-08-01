#!/bin/bash

##############################################################################
# Service Health Check Script
#
# Checks the health status of all microservices and infrastructure components.
#
# Usage: ./check-services.sh [--maven|--docker]
#   --maven   Maven/local ports (run-all-services.sh): Notification 8087,
#             Search 8089, Media 8090, Promotion 8091, Recommendation 8092,
#             Review 8093
#   --docker  docker-compose published ports: Search 8088, Media 8089,
#             Promotion 8090 (notification/recommendation/review are not in
#             the local compose stack)
#   (no flag) auto-detect: promotion on 8091 -> maven, search on 8088 -> docker,
#             otherwise defaults to maven
#
# Grafana/Prometheus (docker-compose.monitoring.yml) are reported but optional:
# they never fail the check.
##############################################################################

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

MODE=""
case "${1:-}" in
  --docker) MODE="docker" ;;
  --maven)  MODE="maven" ;;
  "") ;;
  *)
    echo "Usage: $0 [--maven|--docker]"
    exit 2
    ;;
esac

probe() {
  curl -s -o /dev/null --max-time 2 -w "%{http_code}" "http://localhost:$1/actuator/health" 2>/dev/null
}

if [ -z "$MODE" ]; then
  if [ "$(probe 8091)" = "200" ]; then
    MODE="maven"
  elif [ "$(probe 8088)" = "200" ]; then
    MODE="docker"
  else
    MODE="maven"
  fi
fi

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}E-Commerce Platform Health Check (${MODE} ports)${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

services=(
  "Eureka:8761"
  "Config:8888"
  "Gateway:8080"
  "User:8081"
  "Product:8082"
  "Cart:8083"
  "Order:8084"
  "Payment:8085"
  "Inventory:8086"
)

if [ "$MODE" = "docker" ]; then
  services+=(
    "Search:8088"
    "Media:8089"
    "Promotion:8090"
  )
else
  services+=(
    "Notification:8087"
    "Search:8089"
    "Media:8090"
    "Promotion:8091"
    "Recommendation:8092"
    "Review:8093"
  )
fi

services+=("Zipkin:9411")

# Only present when docker-compose.monitoring.yml is up — never fail on these.
optional_services=(
  "Grafana:3000"
  "Prometheus:9090"
)

check_endpoint() {
  local name=$1 port=$2
  if [ "$name" = "Zipkin" ]; then
    echo "http://localhost:$port/health"
  elif [ "$name" = "Grafana" ]; then
    echo "http://localhost:$port/api/health"
  elif [ "$name" = "Prometheus" ]; then
    echo "http://localhost:$port/-/healthy"
  else
    echo "http://localhost:$port/actuator/health"
  fi
}

UP_COUNT=0
DOWN_COUNT=0

for service in "${services[@]}"; do
  name="${service%%:*}"
  port="${service##*:}"
  endpoint="$(check_endpoint "$name" "$port")"

  status=$(curl -s -o /dev/null --max-time 3 -w "%{http_code}" "$endpoint" 2>/dev/null)

  if [ "$status" = "200" ]; then
    echo -e "${GREEN}✅ $name${NC} (port $port): UP"
    UP_COUNT=$((UP_COUNT + 1))
  else
    echo -e "${RED}❌ $name${NC} (port $port): DOWN (HTTP $status)"
    DOWN_COUNT=$((DOWN_COUNT + 1))
  fi
done

echo ""
for service in "${optional_services[@]}"; do
  name="${service%%:*}"
  port="${service##*:}"
  endpoint="$(check_endpoint "$name" "$port")"

  status=$(curl -s -o /dev/null --max-time 3 -w "%{http_code}" "$endpoint" 2>/dev/null)

  if [ "$status" = "200" ]; then
    echo -e "${GREEN}✅ $name${NC} (port $port): UP (optional)"
  else
    echo -e "${YELLOW}⚪ $name${NC} (port $port): not running (optional, docker-compose.monitoring.yml)"
  fi
done

echo ""
echo -e "${BLUE}============================================${NC}"
echo -e "Total Services: ${BLUE}${#services[@]}${NC}"
echo -e "Running: ${GREEN}$UP_COUNT${NC}"
echo -e "Down: ${RED}$DOWN_COUNT${NC}"
echo -e "${BLUE}============================================${NC}"

if [ $DOWN_COUNT -gt 0 ]; then
  echo ""
  echo -e "${YELLOW}Some services are down. To start all services:${NC}"
  echo "  docker-compose up -d   # For Docker deployment (then: $0 --docker)"
  echo "  ./scripts/run-all-services.sh   # For local development (then: $0 --maven)"
  exit 1
fi
