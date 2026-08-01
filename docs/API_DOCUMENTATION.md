# E-Commerce Platform API Documentation

> Back to [README](../README.md).

## Overview

This document provides comprehensive documentation for all API endpoints across the e-commerce microservices platform. All services are accessible through the API Gateway at `http://localhost:8080`.

## API Gateway Configuration

### Base URL
- **Gateway**: `http://localhost:8080`
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **GraphQL BFF**: `POST http://localhost:8080/graphql` — single-round-trip
  aggregation for the MFEs. See [`GRAPHQL_BFF.md`](./GRAPHQL_BFF.md) for the
  schema, sample queries, DataLoader behaviour and auth surface. GraphiQL
  is exposed at `http://localhost:8080/graphiql` outside production.

### Authentication
All endpoints (except public ones) require JWT authentication via Auth0.

**Headers**:
```
Authorization: Bearer <JWT_TOKEN>
```

### Rate Limiting
Rate limits are implemented per service using Redis:
- User Service: 100 req/s (burst: 200)
- Product Service: 200 req/s (burst: 400)
- Cart Service: 100 req/s (burst: 200)
- Order Service: 50 req/s (burst: 100)
- Payment Service: 50 req/s (burst: 100)
- Inventory Service: 100 req/s (burst: 200)
- Notification Service: 50 req/s (burst: 100)
- Search Service: 200 req/s (burst: 400)
- Media Service: 50 req/s (burst: 100)
- Promotion Service: 100 req/s (burst: 200)

---

## 1. User Service

**Base Path**: `/api/users`

### Endpoints

#### Get User Profile
```
GET /api/users/{userId}
```
**Authentication**: Required
**Response**: User profile details

#### Update User Profile
```
PUT /api/users/{userId}
```
**Authentication**: Required
**Request Body**:
```json
{
  "firstName": "string",
  "lastName": "string",
  "email": "string",
  "phone": "string"
}
```

#### List User Addresses
```
GET /api/users/{userId}/addresses
```
**Authentication**: Required
**Response**: List of user addresses

---

## 2. Product Service

**Base Path**: `/api/products`, `/api/categories`

### Public Endpoints

#### List Products
```
GET /api/products
```
**Authentication**: Not required
**Query Parameters**:
- `page` (optional): Page number (default: 0)
- `size` (optional): Page size (default: 20)
- `category` (optional): Filter by category
- `minPrice` (optional): Minimum price
- `maxPrice` (optional): Maximum price

#### Get Product Details
```
GET /api/products/{productId}
```
**Authentication**: Not required

#### List Categories
```
GET /api/categories
```
**Authentication**: Not required

### Admin Endpoints

#### Create Product
```
POST /api/products
```
**Authentication**: Required (Admin)
**Request Body**:
```json
{
  "name": "string",
  "description": "string",
  "price": "number",
  "categoryId": "string",
  "sku": "string",
  "inventory": "number"
}
```

#### Update Product
```
PUT /api/products/{productId}
```
**Authentication**: Required (Admin)

#### Delete Product
```
DELETE /api/products/{productId}
```
**Authentication**: Required (Admin)

---

## 3. Search Service

**Base Path**: `/api/search`

### Endpoints

#### Search Products
```
GET /api/search
```
**Authentication**: Not required
**Query Parameters**:
- `q` (required): Search query
- `page` (optional): Page number
- `size` (optional): Page size
- `category` (optional): Filter by category
- `minPrice` (optional): Minimum price
- `maxPrice` (optional): Maximum price
- `sort` (optional): Sort field

**Response**:
```json
{
  "content": [...],
  "totalElements": 0,
  "totalPages": 0,
  "page": 0,
  "size": 20
}
```

#### Search Suggestions
```
GET /api/search/suggestions
```
**Authentication**: Not required
**Query Parameters**:
- `q` (required): Partial search query

---

## 4. Cart Service

**Base Path**: `/api/cart`

### gRPC Endpoints (Internal)
The Cart Service primarily uses gRPC for internal service-to-service communication:
- `GetCart`: Retrieve cart for a user
- `AddItem`: Add item to cart
- `UpdateItemQuantity`: Update quantity
- `RemoveItem`: Remove item from cart
- `ClearCart`: Clear all items

### REST Endpoints

#### Get Cart
```
GET /api/cart
```
**Authentication**: Required

#### Add Item to Cart
```
POST /api/cart/items
```
**Authentication**: Required
**Request Body**:
```json
{
  "productId": "string",
  "quantity": 1
}
```

#### Update Item Quantity
```
PUT /api/cart/items/{itemId}
```
**Authentication**: Required
**Request Body**:
```json
{
  "quantity": 2
}
```

#### Remove Item
```
DELETE /api/cart/items/{itemId}
```
**Authentication**: Required

#### Clear Cart
```
DELETE /api/cart
```
**Authentication**: Required

---

## 5. Promotion Service

**Base Path**: `/api/promotions`

### Public Endpoints

#### Get Active Promotions
```
GET /api/promotions/public/active
```
**Authentication**: Not required
**Response**: List of currently active promotions

### Public Endpoints

#### Validate Promotion
```
POST /api/promotions/validate
```
**Authentication**: None — a guest checks a promo code before logging in.

**Rate limit**: per client IP at the gateway, `GATEWAY_PROMO_VALIDATE_RATE`/
`GATEWAY_PROMO_VALIDATE_BURST` (default 2 req/s, burst 5). Deliberately tight:
a public validate endpoint is a promo-code enumeration oracle. Exceeding it
returns `429 Too Many Requests`.

**Request Body**:
```json
{
  "code": "string",
  "purchaseAmount": "number",
  "categoryId": "number (optional)"
}
```
**Response**:
```json
{
  "valid": true,
  "discountAmount": 10.00,
  "message": "Promotion applied successfully"
}
```

### Service-to-Service Endpoints

#### Apply Promotion
```
POST /api/promotions/apply
```
**Authentication**: Internal service credential — the request MUST carry
`X-Internal-Service-Token` matching the deployment's `INTERNAL_SERVICE_TOKEN`.
A user JWT (including `SCOPE_admin`) is NOT sufficient: this call increments the
promotion usage counter and is issued only by order-service's checkout saga,
on both the authenticated and the guest path.

Not callable from a browser: the API gateway strips any client-supplied
`X-Internal-Service-Token` and does not exempt this path from authentication.
Unauthorized callers get `401` (no/invalid token) or `403` (user JWT).

**Request Body**: same shape as `/validate`.
```json
{
  "code": "string",
  "purchaseAmount": "number",
  "categoryId": "number (optional)"
}
```

### Admin Endpoints

#### Create Promotion
```
POST /api/promotions
```
**Authentication**: Required (Admin)
**Request Body**:
```json
{
  "code": "SUMMER2025",
  "description": "Summer Sale",
  "type": "PERCENTAGE",
  "discountValue": 20.0,
  "minOrderAmount": 50.00,
  "maxDiscountAmount": 100.00,
  "startDate": "2025-06-01T00:00:00",
  "endDate": "2025-08-31T23:59:59",
  "usageLimit": 1000,
  "userUsageLimit": 1
}
```

#### Get Promotion
```
GET /api/promotions/{promotionId}
```
**Authentication**: Required (Admin)

#### List All Promotions
```
GET /api/promotions
```
**Authentication**: Required (Admin)

#### Update Promotion
```
PUT /api/promotions/{promotionId}
```
**Authentication**: Required (Admin)

#### Delete Promotion
```
DELETE /api/promotions/{promotionId}
```
**Authentication**: Required (Admin)

---

## 6. Order Service

**Base Path**: `/api/orders`

### Endpoints

#### Create Order
```
POST /api/orders
```
**Authentication**: Required
**Request Body**:
```json
{
  "shippingAddress": {
    "street": "string",
    "city": "string",
    "state": "string",
    "zipCode": "string",
    "country": "string"
  },
  "promotionCode": "SUMMER2025"
}
```
**Response**:
```json
{
  "orderId": "string",
  "orderNumber": "ORD2025001234",
  "status": "PENDING",
  "subtotal": 100.00,
  "tax": 8.00,
  "shippingCost": 5.99,
  "discountAmount": 20.00,
  "total": 93.99
}
```

#### Get Order
```
GET /api/orders/{orderId}
```
**Authentication**: Required

#### List User Orders
```
GET /api/orders
```
**Authentication**: Required
**Query Parameters**:
- `page` (optional): Page number
- `size` (optional): Page size
- `status` (optional): Filter by status

#### Cancel Order
```
POST /api/orders/{orderId}/cancel
```
**Authentication**: Required

#### Get Order Status
```
GET /api/orders/{orderId}/status
```
**Authentication**: Required

### Order Status Flow
```
PENDING -> CONFIRMED -> PROCESSING -> SHIPPED -> DELIVERED
       -> CANCELLED (from PENDING or CONFIRMED)
       -> REFUNDED (from DELIVERED)
```

---

## 7. Payment Service

**Base Path**: `/api/payments`

### gRPC Endpoints (Internal)
- `ProcessPayment`: Process payment for an order
- `RefundPayment`: Refund a completed payment
- `GetPaymentStatus`: Get payment status

### REST Endpoints

#### Process Payment
```
POST /api/payments
```
**Authentication**: Required
**Request Body**:
```json
{
  "orderId": "string",
  "amount": 93.99,
  "paymentMethod": {
    "type": "CARD",
    "token": "stripe_token"
  }
}
```

#### Get Payment
```
GET /api/payments/{paymentId}
```
**Authentication**: Required

#### Refund Payment
```
POST /api/payments/{paymentId}/refund
```
**Authentication**: Required
**Request Body**:
```json
{
  "amount": 93.99,
  "reason": "Customer request"
}
```

---

## 8. Inventory Service

**Base Path**: `/api/inventory`

### gRPC Endpoints (Internal)
- `ReserveInventory`: Reserve stock for order items
- `ReleaseInventory`: Release reserved stock
- `UpdateStock`: Update stock levels
- `CheckAvailability`: Check stock availability

### REST Endpoints

#### Check Product Availability
```
GET /api/inventory/{productId}
```
**Authentication**: Required

#### Get Stock Level
```
GET /api/inventory/{productId}/stock
```
**Authentication**: Required

#### Update Stock (Admin)
```
PUT /api/inventory/{productId}/stock
```
**Authentication**: Required (Admin)
**Request Body**:
```json
{
  "quantity": 100,
  "operation": "ADD"
}
```

---

## 9. Notification Service

**Base Path**: `/api/notifications`

### Endpoints

#### Get User Notifications
```
GET /api/notifications
```
**Authentication**: Required
**Query Parameters**:
- `page` (optional): Page number
- `size` (optional): Page size
- `unreadOnly` (optional): Filter unread notifications

#### Mark as Read
```
PUT /api/notifications/{notificationId}/read
```
**Authentication**: Required

#### Mark All as Read
```
PUT /api/notifications/read-all
```
**Authentication**: Required

### Event-Driven Notifications
The service listens to Kafka topics and sends notifications for:
- Order confirmation
- Payment confirmation
- Shipping updates
- Promotion announcements

---

## 10. Media Service

**Base Path**: `/api/media`

### Endpoints

#### Upload File
```
POST /api/media/upload
```
**Authentication**: Required
**Content-Type**: `multipart/form-data`
**Form Data**:
- `file`: The file to upload
- `type`: File type (IMAGE, VIDEO, DOCUMENT)

**Response**:
```json
{
  "mediaId": "string",
  "url": "https://cdn.example.com/...",
  "type": "IMAGE",
  "size": 12345,
  "filename": "product-image.jpg"
}
```

#### Get Media
```
GET /api/media/{mediaId}
```
**Authentication**: Not required

#### Delete Media
```
DELETE /api/media/{mediaId}
```
**Authentication**: Required

#### List User Media
```
GET /api/media
```
**Authentication**: Required

---

## Integration Patterns

### Order Creation Flow

1. **Client** → `POST /api/orders`
2. **Order Service** validates request
3. **Order Service** → **Promotion Service** (gRPC): Validate promotion code
4. **Order Service** → **Cart Service** (gRPC): Get cart items
5. **Order Service** → **Inventory Service** (gRPC): Reserve stock
6. **Order Service** → **Payment Service** (gRPC): Process payment
7. **Order Service** creates order and publishes `OrderCreated` event
8. **Notification Service** consumes event and sends confirmation email
9. **Cart Service** consumes event and clears cart

### Promotion Application Flow

1. **Client** → `POST /api/promotions/validate` with promotion code
2. **Promotion Service** validates:
   - Promotion exists and is active
   - User hasn't exceeded usage limits
   - Order meets minimum amount requirements
   - Promotion dates are valid
3. **Promotion Service** calculates discount
4. **Client** → `POST /api/orders` with validated promotion code
5. **Order Service** → **Promotion Service** (gRPC): Apply promotion
6. **Promotion Service** increments usage counters
7. **Order Service** applies discount to order total

### Search Indexing Flow

1. **Product Service** publishes `ProductCreated/Updated/Deleted` events to Kafka
2. **Search Service** consumes events
3. **Search Service** indexes/updates/removes documents in Elasticsearch
4. **Client** searches via `GET /api/search?q=...`
5. **Search Service** queries Elasticsearch and returns results

---

## Error Responses

All services follow a consistent error response format:

```json
{
  "timestamp": "2025-12-21T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/orders",
  "details": {
    "shippingAddress": "Shipping address is required"
  }
}
```

### Common HTTP Status Codes

- `200 OK`: Successful request
- `201 Created`: Resource created successfully
- `400 Bad Request`: Invalid request data
- `401 Unauthorized`: Missing or invalid authentication
- `403 Forbidden`: Insufficient permissions
- `404 Not Found`: Resource not found
- `409 Conflict`: Resource conflict (e.g., duplicate)
- `429 Too Many Requests`: Rate limit exceeded
- `500 Internal Server Error`: Server error
- `503 Service Unavailable`: Service temporarily unavailable

---

## Monitoring and Observability

### Health Checks
All services expose health endpoints:
```
GET /actuator/health
```

### Metrics
Prometheus metrics available at:
```
GET /actuator/prometheus
```

### Distributed Tracing
All requests are traced via Zipkin:
- **Trace ID** is included in response headers as `X-B3-TraceId`
- View traces at: `http://localhost:9411`

### API Gateway Metrics Endpoint
```
GET /actuator/gateway/routes
```
Returns all configured routes with their current status.

---

## Swagger Documentation

Interactive API documentation is available via Swagger UI:

**URL**: `http://localhost:8080/swagger-ui.html`

Select a service from the dropdown to view its API documentation:
- user-service
- product-service
- cart-service
- order-service
- payment-service
- inventory-service
- notification-service
- search-service
- media-service
- promotion-service

---

## WebSocket Endpoints

### Real-time Order Updates
```
WS /api/orders/ws
```
**Authentication**: Required via query param `?token=<JWT>`

Subscribe to order updates:
```json
{
  "action": "subscribe",
  "orderId": "string"
}
```

Receive updates:
```json
{
  "orderId": "string",
  "status": "SHIPPED",
  "timestamp": "2025-12-21T10:30:00Z",
  "message": "Your order has been shipped"
}
```

---

## Best Practices

### Pagination
All list endpoints support pagination:
- Default page size: 20
- Max page size: 100
- Use `page` and `size` query parameters

### Filtering and Sorting
- Use query parameters for filtering: `?category=electronics&minPrice=100`
- Sort using `sort` parameter: `?sort=price,asc`

### Idempotency
- Use `Idempotency-Key` header for POST requests
- Key is valid for 24 hours
- Duplicate requests with same key return cached response

### Versioning
Current API version: v1 (no version prefix required)
Future versions will use path prefix: `/api/v2/...`

### Rate Limit Headers
All responses include rate limit information:
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1640089200
```

---

## Support

For API issues or questions:
- Check service logs via `docker-compose logs <service-name>`
- View Grafana dashboards at `http://localhost:3000`
- View Zipkin traces at `http://localhost:9411`
- Contact: api-support@ecommerce.local
