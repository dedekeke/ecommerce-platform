# Cart Service

Shopping cart management service for the e-commerce platform.

## Overview

The Cart Service manages shopping cart operations including adding items, updating quantities, and preparing carts for checkout. It integrates with the Product Service to validate product availability and capture price snapshots.

## Features

- **Cart Management**: Create and manage shopping carts per user
- **Item Operations**: Add, update, and remove items from cart
- **Price Snapshots**: Capture product prices when added to cart
- **Stock Validation**: Validate product availability through Product Service
- **Cart Expiration**: Automatic cart expiration after 30 days of inactivity
- **Redis Caching**: Fast cart data access with Redis
- **Auth0 Integration**: Secure endpoints with OAuth2 JWT validation

## Technology Stack

- **Framework**: Spring Boot 3.2.0
- **Language**: Java 21
- **Database**: PostgreSQL (transactional data)
- **Cache**: Redis (cart caching)
- **Message Queue**: Kafka (event streaming)
- **Service Discovery**: Eureka Client
- **Config Management**: Spring Cloud Config
- **API Communication**: OpenFeign
- **Migration**: Flyway
- **API Documentation**: OpenAPI/Swagger

## Architecture

### Domain Model

```
Cart (1) <---> (N) CartItem
```

- **Cart**: Represents a user's shopping cart
  - User association via Auth0 sub claim
  - Status tracking (ACTIVE, CHECKED_OUT, ABANDONED, MERGED)
  - Automatic total calculation
  - Expiration support

- **CartItem**: Individual items in the cart
  - Product reference (productId)
  - Price snapshot (price at time of adding)
  - Quantity management
  - Subtotal calculation

### API Endpoints

#### Cart Operations

```
GET    /api/cart              - Get or create user's cart
POST   /api/cart/items        - Add item to cart
PUT    /api/cart/items/{id}   - Update cart item quantity
DELETE /api/cart/items/{id}   - Remove item from cart
DELETE /api/cart/clear         - Clear all items from cart
DELETE /api/cart               - Delete entire cart
```

All endpoints require authentication (JWT Bearer token).

### Service Integration

- **Product Service**: Fetch product details and validate stock
- **User Service**: Validate user existence (future)
- **Order Service**: Convert cart to order on checkout (future)

## Database Schema

### carts table
- id (BIGSERIAL, PK)
- user_id (VARCHAR, indexed)
- status (VARCHAR)
- total_amount (DECIMAL)
- total_items (INTEGER)
- expires_at (TIMESTAMP, indexed)
- created_at (TIMESTAMP)
- updated_at (TIMESTAMP)

### cart_items table
- id (BIGSERIAL, PK)
- cart_id (BIGINT, FK -> carts.id, indexed)
- product_id (BIGINT, indexed)
- product_name (VARCHAR)
- product_sku (VARCHAR)
- product_image_url (VARCHAR)
- price_snapshot (DECIMAL)
- quantity (INTEGER)
- subtotal (DECIMAL)
- created_at (TIMESTAMP)
- updated_at (TIMESTAMP)

## Configuration

### Environment Variables

- `SPRING_DATASOURCE_URL`: PostgreSQL connection URL
- `SPRING_DATASOURCE_USERNAME`: Database username
- `SPRING_DATASOURCE_PASSWORD`: Database password
- `SPRING_DATA_REDIS_HOST`: Redis host
- `SPRING_DATA_REDIS_PORT`: Redis port
- `SPRING_KAFKA_BOOTSTRAP_SERVERS`: Kafka broker addresses
- `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`: Eureka server URL
- `AUTH0_ISSUER_URI`: Auth0 issuer URI
- `AUTH0_AUDIENCE`: Auth0 API audience

### Application Profiles

- `local`: Local development (standalone mode)
- `docker`: Docker deployment

## Running the Service

### Local Development

```bash
# Start PostgreSQL and Redis
docker-compose up -d postgres redis

# Run the service
mvn spring-boot:run -pl services/cart-service -Dspring-boot.run.profiles=local
```

### Docker Deployment

```bash
# Build and run all services
docker-compose up --build cart-service
```

## API Documentation

Once the service is running, access the API documentation at:
- Swagger UI: http://localhost:8083/swagger-ui.html
- OpenAPI JSON: http://localhost:8083/v3/api-docs

## Health Checks

- Health endpoint: http://localhost:8083/actuator/health
- Info endpoint: http://localhost:8083/actuator/info

## Future Enhancements

### High Priority
1. **Redis Caching**: Implement Redis caching for cart data to reduce database load
2. **Cart Cleanup Job**: Scheduled job to clean up expired/abandoned carts
3. **Anonymous Cart Support**: Support for anonymous users with cart merge on login
4. **Price Validation**: Re-validate prices on checkout to handle price changes

### Medium Priority
5. **Coupon Integration**: Support for applying promotional codes
6. **Saved for Later**: Move items to "saved for later" list
7. **Cart Recommendations**: Suggest products based on cart contents
8. **Stock Reservation**: Reserve stock when items are added to cart

### Low Priority
9. **Cart Sharing**: Share cart via link
10. **Wishlist Integration**: Move items between cart and wishlist
11. **Cart Analytics**: Track cart abandonment metrics
12. **Multi-currency Support**: Handle different currencies

## Testing

```bash
# Run unit tests
mvn test -pl services/cart-service

# Run integration tests
mvn verify -pl services/cart-service

# Run with coverage
mvn clean verify -pl services/cart-service jacoco:report
```

## Monitoring

The service exposes the following metrics:
- JVM metrics (memory, threads, GC)
- HTTP metrics (request count, duration)
- Database connection pool metrics
- Custom business metrics (cart operations, item counts)

Access metrics at: http://localhost:8083/actuator/metrics

## Security

- All API endpoints require valid Auth0 JWT token
- User can only access their own cart
- Price snapshots prevent price manipulation
- Input validation on all requests
- SQL injection prevention through JPA/Hibernate

## Error Handling

The service returns standardized error responses:

```json
{
  "timestamp": "2024-11-15T00:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Cart not found for user",
  "path": "/api/cart"
}
```

## License

Copyright (c) 2024 E-Commerce Platform
