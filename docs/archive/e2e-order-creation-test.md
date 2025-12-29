# E2E Order Creation Flow Test

This document describes the end-to-end test process for the order creation flow with Auth0 security disabled for development.

## Prerequisites

- Docker and Docker Compose installed
- All infrastructure services running (Postgres, Redis, Kafka, Eureka, Zipkin)
- Security disabled via `SECURITY_ENABLED=false` in environment

## Test Environment Setup

### Start Infrastructure Services
```bash
docker-compose up -d postgres redis kafka eureka-server zipkin
```

### Start Microservices
```bash
docker-compose up -d user-service product-service cart-service order-service
```

### Verify Services are Healthy
```bash
docker-compose ps
```

## Test Data and API Calls

### Step 1: Create Test User

**Endpoint:** `POST http://localhost:8081/api/users`

```bash
curl -X POST "http://localhost:8081/api/users" \
  -H "Content-Type: application/json" \
  -d '{
    "auth0Id": "test-user-e2e-001",
    "email": "e2e-test@example.com",
    "firstName": "Test",
    "lastName": "User"
  }'
```

**Response:**
```json
{
  "id": 321,
  "auth0Id": "test-user-e2e-001",
  "email": "e2e-test@example.com",
  "firstName": "Test",
  "lastName": "User"
}
```

### Step 2: Create Test Product

**Endpoint:** `POST http://localhost:8082/api/products`

```bash
curl -X POST "http://localhost:8082/api/products" \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "E2E-FINAL-001",
    "name": "E2E Final Test Product",
    "description": "Final test",
    "price": 199.99,
    "currency": "USD",
    "stockQuantity": 50
  }'
```

**Response:**
```json
{
  "id": "33",
  "sku": "E2E-FINAL-001",
  "name": "E2E Final Test Product",
  "price": 199.99,
  "currency": "USD",
  "stockQuantity": 50,
  "active": true
}
```

### Step 3: Verify Product is Available

**Endpoint:** `GET http://localhost:8082/api/products/33`

```bash
curl "http://localhost:8082/api/products/33"
```

**Response:**
```json
{
  "id": "33",
  "sku": "E2E-FINAL-001",
  "name": "E2E Final Test Product",
  "price": 199.99,
  "stockQuantity": 50,
  "active": true,
  "inStock": true,
  "available": true
}
```

### Step 4: Add Item to Cart

**Endpoint:** `POST http://localhost:8083/api/cart/{userId}/items`

Note: Using the test endpoint with userId path parameter (security disabled).

```bash
curl -X POST "http://localhost:8083/api/cart/321/items" \
  -H "Content-Type: application/json" \
  -d '{
    "productId": "33",
    "quantity": 2
  }'
```

**Response:**
```json
{
  "id": "3",
  "userId": "321",
  "items": [
    {
      "id": "1",
      "productId": "33",
      "productName": "E2E Final Test Product",
      "productSku": "E2E-FINAL-001",
      "price": 199.99,
      "quantity": 2,
      "subtotal": 399.98
    }
  ],
  "totalAmount": 399.98,
  "totalItems": 2,
  "status": "ACTIVE"
}
```

### Step 5: Create Order

**Endpoint:** `POST http://localhost:8084/api/orders`

```bash
curl -X POST "http://localhost:8084/api/orders" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "321",
    "shippingAddress": {
      "street": "123 Test Street",
      "city": "San Francisco",
      "state": "CA",
      "postalCode": "94102",
      "country": "USA"
    }
  }'
```

**Response:**
```json
{
  "id": "6d162740-a7bb-442d-9911-0978b564d1dc",
  "orderNumber": "ORD-2025-00002",
  "userId": "321",
  "items": [...],
  "subtotal": 10.00,
  "tax": 0.80,
  "shippingCost": 5.99,
  "total": 16.79,
  "status": "PENDING",
  "shippingAddress": {
    "street": "123 Test Street",
    "city": "San Francisco",
    "state": "CA",
    "postalCode": "94102",
    "country": "USA"
  }
}
```

### Step 6: Verify Order

**Endpoint:** `GET http://localhost:8084/api/orders/user/321`

```bash
curl "http://localhost:8084/api/orders/user/321"
```

## Service Ports

| Service         | HTTP Port | gRPC Port |
|-----------------|-----------|-----------|
| User Service    | 8081      | -         |
| Product Service | 8082      | -         |
| Cart Service    | 8083      | -         |
| Order Service   | 8084      | 9094      |
| API Gateway     | 8080      | -         |
| Eureka Server   | 8761      | -         |

## Fixes Applied During Testing

### 1. Product Service - Redis Serialization Fix
- Made `Product`, `Category`, and `Dimensions` classes implement `Serializable`
- Changed `@ElementCollection` to `@ElementCollection(fetch = FetchType.EAGER)` for images to avoid LazyInitializationException

### 2. Cart Service - ProductDto Field Mismatch Fix
- Changed `status` (String) field to `active` (Boolean) in `ProductDto.java`
- Updated validation in `CartService.validateProductAvailability()` to check `product.getActive()` instead of `product.getStatus()`
- Created `application-docker.yml` with proper Eureka configuration

### 3. Order Service - Security and Entity Fixes
- Created `SecurityConfig.java` to handle `SECURITY_ENABLED` flag
- Fixed `Order.calculateTotals()` to handle null item subtotals
- Added null checks for `tax` and `shippingCost` fields
- Added `@JsonIgnore` on `OrderItem.order` to prevent circular JSON reference

### 4. Docker Runtime Improvements
- Created `Dockerfile.runtime` for faster deployment using pre-built JARs
- Updated `docker-compose.yml` to use runtime Dockerfiles for product-service, cart-service, and order-service

## Known Issues

1. **Order Service Dummy Data**: The current `createOrderFromCart` implementation creates dummy order items instead of fetching actual cart items. Full integration requires implementing cart-service to order-service communication.

2. **Inventory Not Validated**: The order creation does not validate inventory availability. This should be implemented in the full saga pattern.

## Next Steps

1. Implement cart-to-order integration to use actual cart items
2. Add inventory reservation during order creation
3. Implement payment processing integration
4. Add order status update workflow (PENDING -> CONFIRMED -> SHIPPED -> DELIVERED)

## Date

December 28, 2025
