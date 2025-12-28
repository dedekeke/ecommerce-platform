#!/bin/bash

##############################################################################
# Service Health Check Script
#
# Checks the health status of all microservices and infrastructure components
##############################################################################

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}E-Commerce Platform Health Check${NC}"
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
  "Notification:8087"
  "Search:8089"
  "Media:8090"
  "Promotion:8091"
  "Zipkin:9411"
  "Grafana:3000"
  "Prometheus:9090"
)

UP_COUNT=0
DOWN_COUNT=0

for service in "${services[@]}"; do
  name="${service%%:*}"
  port="${service##*:}"

  if [ "$name" = "Zipkin" ]; then
    endpoint="http://localhost:$port/health"
  elif [ "$name" = "Grafana" ]; then
    endpoint="http://localhost:$port/api/health"
  elif [ "$name" = "Prometheus" ]; then
    endpoint="http://localhost:$port/-/healthy"
  else
    endpoint="http://localhost:$port/actuator/health"
  fi

  status=$(curl -s -o /dev/null -w "%{http_code}" "$endpoint" 2>/dev/null)

  if [ "$status" = "200" ]; then
    echo -e "${GREEN}✅ $name${NC} (port $port): UP"
    UP_COUNT=$((UP_COUNT + 1))
  else
    echo -e "${RED}❌ $name${NC} (port $port): DOWN (HTTP $status)"
    DOWN_COUNT=$((DOWN_COUNT + 1))
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
  echo "  docker-compose up -d   # For Docker deployment"
  echo "  ./scripts/run-all-services.sh   # For local development"
  exit 1
fi