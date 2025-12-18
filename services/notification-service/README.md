# Notification Service

Event-driven notification service supporting email and SMS with template management.

## Features

- Email notifications via JavaMailSender (mock/real SMTP)
- SMS notifications (Twilio mock)
- Template management with Thymeleaf
- Event-driven via Kafka consumers
- Notification history and retry logic
- MongoDB for templates and logs

## Tech Stack

- Spring Boot 3.2.0
- Spring Data MongoDB
- Spring Kafka
- Thymeleaf (templates)
- JavaMailSender (email)
- Eureka Client (service discovery)

## API Endpoints

### Notification History
- `GET /api/notifications` - Get all notifications (paginated)
- `GET /api/notifications/{id}` - Get notification by ID
- `GET /api/notifications/user/{userId}` - Get user notifications
- `POST /api/notifications/retry/{id}` - Retry failed notification

### Templates
- `GET /api/templates` - List all templates
- `GET /api/templates/{code}` - Get template by code
- `POST /api/templates` - Create template (admin)
- `PUT /api/templates/{code}` - Update template (admin)

## Event Consumers

- `order.created` → Order Confirmation Email
- `payment.completed` → Payment Receipt Email
- `order.shipped` → Shipping Notification Email/SMS

## Configuration

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://mongo:27017/notificationdb
  mail:
    host: ${MAIL_HOST:smtp.gmail.com}
    port: ${MAIL_PORT:587}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
  kafka:
    bootstrap-servers: kafka:29092
    consumer:
      group-id: notification-service

notification:
  email:
    from: noreply@ecommerce.com
    enabled: ${EMAIL_ENABLED:false}
  sms:
    enabled: ${SMS_ENABLED:false}
    provider: twilio
```

## Running

```bash
# Build
mvn clean package -DskipTests

# Run locally
mvn spring-boot:run

# Docker
docker-compose up -d notification-service
```

## Port

- HTTP: 8087
- MongoDB: 27017

## Day 21 Implementation Status

✅ Project structure created
✅ MongoDB domain models
✅ Email service with mock
✅ SMS service with mock
✅ Kafka event consumers
✅ Thymeleaf templates
✅ REST API for history
✅ Retry logic
✅ Dockerized
