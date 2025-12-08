#!/bin/bash

echo "Checking all services..."
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
  "Zipkin:9411"
)

for service in "${services[@]}"; do
  name="${service%%:*}"
  port="${service##*:}"

  if [ "$name" = "Zipkin" ]; then
    endpoint="http://localhost:$port/health"
  else
    endpoint="http://localhost:$port/actuator/health"
  fi

  status=$(curl -s -o /dev/null -w "%{http_code}" "$endpoint")

  if [ "$status" = "200" ]; then
    echo "✅ $name ($port): UP"
  else
    echo "❌ $name ($port): DOWN (HTTP $status)"
  fi
done