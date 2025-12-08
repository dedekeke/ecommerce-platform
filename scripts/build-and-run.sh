#!/bin/bash

# Build and Run Script for E-Commerce Platform
# This script builds the services and starts them with Docker Compose

set -e  # Exit on error

echo "======================================"
echo "E-Commerce Platform - Build & Deploy"
echo "======================================"
echo ""

# Check if .env file exists
if [ ! -f .env ]; then
    echo "⚠️  Warning: .env file not found!"
    echo "Creating .env from .env.example..."
    if [ -f .env.example ]; then
        cp .env.example .env
        echo "✅ Created .env file. Please edit it with your Auth0 credentials."
        exit 1
    else
        echo "❌ Error: .env.example not found. Please create .env manually."
        exit 1
    fi
fi

echo "Step 1: Building Java services..."
echo "-----------------------------------"

# Build all services
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "❌ Maven build failed!"
    exit 1
fi

echo ""
echo "✅ Build completed successfully!"
echo ""

echo "Step 2: Starting Docker containers..."
echo "--------------------------------------"

# Start Docker Compose
docker-compose up -d

if [ $? -ne 0 ]; then
    echo "❌ Docker Compose failed!"
    exit 1
fi

echo ""
echo "✅ All services started!"
echo ""
echo "======================================"
echo "Service URLs:"
echo "======================================"
echo "Eureka Server:       http://localhost:8761"
echo "Config Server:       http://localhost:8888"
echo "API Gateway:         http://localhost:8080"
echo "User Service:        http://localhost:8081/swagger-ui.html"
echo "Product Service:     http://localhost:8082/swagger-ui.html"
echo "Cart Service:        http://localhost:8083/swagger-ui.html"
echo "Order Service:       http://localhost:8084/swagger-ui.html"
echo "Payment Service:     http://localhost:8085/swagger-ui.html"
echo "Inventory Service:   http://localhost:8086/swagger-ui.html"
echo "Zipkin:              http://localhost:9411"
echo "Grafana:             http://localhost:3000 (admin/admin)"
echo "======================================"
echo ""
echo "To check services:   ./scripts/check-services.sh"
echo "To view logs:        docker-compose logs -f [service-name]"
echo "To stop:             docker-compose down"
echo ""
