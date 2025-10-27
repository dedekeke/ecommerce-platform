# E-Commerce Platform - Microservices Architecture

A production-ready e-commerce platform built with modern microservices architecture, combining React 19 and Angular micro-frontends with Java 21 Spring Boot backend services.

## 🏗️ Architecture Overview

This platform implements a sophisticated microservices architecture with:

- **11 Backend Microservices** (Java 21 + Spring Boot 3.2+)
- **React 19 Micro-Frontends** (Product Catalog, Cart, Checkout)
- **Angular Micro-Frontends** (User Dashboard, Admin Panel)
- **gRPC** for high-performance internal communication
- **REST APIs** for public-facing endpoints
- **Event-Driven Architecture** with Apache Kafka
- **Comprehensive Observability** (Prometheus, Grafana, Zipkin)

## 📋 Technology Stack

### Backend
- **Java 21** with Virtual Threads for improved concurrency
- **Spring Boot 3.2+** with Spring Cloud
- **gRPC** for internal service communication
- **Apache Kafka** for event streaming
- **PostgreSQL** for transactional data
- **MongoDB** for document storage
- **Redis** for caching and rate limiting
- **Elasticsearch** for product search

### Frontend
- **React 19** with Vite
- **Angular** (latest version)
- **Module Federation** for micro-frontend architecture
- **Auth0** for authentication
- **Material-UI** for React components
- **Zustand** for state management

### Infrastructure
- **Eureka** for service discovery
- **Spring Cloud Gateway** for API gateway
- **Docker Compose** for local development
- **Prometheus + Grafana** for monitoring
- **Zipkin** for distributed tracing

## 🚀 Quick Start

### Prerequisites

- **Java 21** (JDK 21+)
- **Maven 3.8+**
- **Docker Desktop** (or Docker + Docker Compose)
- **Node.js 18+** (for frontend development)
- **Git**

### 1. Clone the Repository

```bash
git clone https://github.com/your-org/ecommerce-platform.git
cd ecommerce-platform
```

### 2. Setup Environment Variables

```bash
cp .env.template .env
# Edit .env and fill in your Auth0 credentials and other configuration
```

**Important:** You need to set up an Auth0 account and configure:
- Auth0 Domain
- Auth0 Client ID
- Auth0 Client Secret
- Auth0 API Audience

See [Auth0 Setup Guide](#auth0-setup) for detailed instructions.

### 3. Build the Project

```bash
# Build all modules
mvn clean install

# Or build specific module
cd common-library
mvn clean install
```

### 4. Start Infrastructure Services

```bash
# Start all infrastructure services (PostgreSQL, MongoDB, Kafka, Redis, etc.)
cd docker
docker-compose up -d

# Check service health
docker-compose ps
```

### 5. Run Microservices

Each microservice can be run independently:

```bash
# Example: Run User Service
cd services/user-service
mvn spring-boot:run

# Or using Docker
docker-compose up user-service
```

### 6. Access the Application

- **API Gateway:** http://localhost:8080
- **Eureka Dashboard:** http://localhost:8761
- **Grafana:** http://localhost:3000 (admin/admin)
- **Zipkin:** http://localhost:9411
- **Prometheus:** http://localhost:9090

## 📁 Project Structure

```
ecommerce-platform/
├── common-library/              # Shared utilities, DTOs, proto definitions
│   ├── src/main/java/com/ecommerce/common/
│   │   ├── dto/                # Common data transfer objects
│   │   ├── exception/          # Common exceptions
│   │   ├── util/               # Utility classes
│   │   └── constants/          # Application constants
│   └── src/main/proto/         # gRPC proto definitions
│
├── infrastructure/              # Infrastructure services
│   ├── eureka-server/          # Service discovery
│   ├── config-server/          # Centralized configuration
│   ├── api-gateway/            # API Gateway with Auth0
│   └── monitoring/             # Monitoring configurations
│
├── services/                    # Business microservices
│   ├── user-service/           # User management (PostgreSQL + REST)
│   ├── product-service/        # Product catalog (PostgreSQL + REST)
│   ├── cart-service/           # Shopping cart (MongoDB + gRPC)
│   ├── order-service/          # Order processing (PostgreSQL + gRPC)
│   ├── payment-service/        # Payment processing (PostgreSQL + gRPC)
│   ├── inventory-service/      # Stock management (PostgreSQL + gRPC)
│   ├── notification-service/   # Email/SMS notifications (MongoDB + Kafka)
│   ├── search-service/         # Elasticsearch-based search
│   ├── media-service/          # Image/file management
│   └── promotion-service/      # Discounts and promotions
│
├── frontend/                    # Frontend applications
│   ├── shell-app/              # Shell app (React 19 + Module Federation)
│   ├── product-catalog-mfe/    # Product catalog MFE (React)
│   ├── cart-mfe/               # Shopping cart MFE (React)
│   ├── checkout-mfe/           # Checkout flow MFE (React)
│   ├── user-dashboard-mfe/     # User dashboard MFE (Angular)
│   └── admin-dashboard-mfe/    # Admin panel MFE (Angular)
│
├── docker/                      # Docker configurations
│   ├── docker-compose.yml      # Main infrastructure services
│   ├── docker-compose.search.yml
│   └── docker-compose.monitoring.yml
│
├── docs/                        # Documentation
│   ├── architecture/           # Architecture diagrams
│   ├── api/                    # API documentation
│   └── guides/                 # Development guides
│
├── .env.template               # Environment variables template
├── .gitignore                  # Git ignore rules
├── pom.xml                     # Parent Maven POM
└── README.md                   # This file
```

## 🔧 Development

### Building Individual Services

```bash
# Build a specific service
cd services/user-service
mvn clean package

# Run tests
mvn test

# Run with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Running Tests

```bash
# Run all tests
mvn test

# Run integration tests
mvn verify

# Generate code coverage report
mvn jacoco:report
# Report will be in target/site/jacoco/index.html
```

### Working with gRPC

```bash
# Generate gRPC classes from proto files
cd common-library
mvn protobuf:compile
mvn protobuf:compile-custom

# Generated files will be in:
# target/generated-sources/protobuf/java/
# target/generated-sources/protobuf/grpc-java/
```

### Database Migrations

```bash
# PostgreSQL databases are automatically created via init script
# Location: docker/init-databases.sql

# Access PostgreSQL
docker exec -it postgres psql -U admin -d userdb

# Access MongoDB
docker exec -it mongodb mongosh -u admin -p admin123
```

## 🔐 Auth0 Setup

### 1. Create Auth0 Account

Visit [auth0.com](https://auth0.com) and create a free account.

### 2. Create API

1. Go to **Applications > APIs**
2. Click **Create API**
3. Name: `E-Commerce Platform API`
4. Identifier: `https://api.ecommerce-platform.com`
5. Signing Algorithm: `RS256`

### 3. Create Application (SPA)

1. Go to **Applications > Applications**
2. Click **Create Application**
3. Name: `E-Commerce Frontend`
4. Type: **Single Page Application**
5. Configure:
   - Allowed Callback URLs: `http://localhost:3000/callback`
   - Allowed Logout URLs: `http://localhost:3000`
   - Allowed Web Origins: `http://localhost:3000`

### 4. Create Application (M2M)

1. Create another application
2. Type: **Machine to Machine**
3. Authorize for your API
4. Grant all permissions

### 5. Update .env File

Copy the credentials to your `.env` file:
```
AUTH0_DOMAIN=your-tenant.auth0.com
AUTH0_CLIENT_ID=<from SPA app>
AUTH0_CLIENT_SECRET=<from SPA app>
AUTH0_AUDIENCE=https://api.ecommerce-platform.com
AUTH0_M2M_CLIENT_ID=<from M2M app>
AUTH0_M2M_CLIENT_SECRET=<from M2M app>
```

## 📊 Monitoring and Observability

### Prometheus Metrics

Access Prometheus at http://localhost:9090

Example queries:
```promql
# Request rate
rate(http_server_requests_seconds_count[5m])

# Error rate
rate(http_server_requests_seconds_count{status="500"}[5m])

# JVM memory usage
jvm_memory_used_bytes
```

### Grafana Dashboards

Access Grafana at http://localhost:3000 (admin/admin)

Pre-configured dashboards:
- JVM Metrics
- HTTP Request Metrics
- Database Metrics
- Kafka Metrics

### Distributed Tracing

Access Zipkin at http://localhost:9411

View traces across all microservices to debug performance issues.

## 🔍 API Documentation

Once the services are running, access Swagger UI:

- API Gateway: http://localhost:8080/swagger-ui.html
- User Service: http://localhost:8081/swagger-ui.html
- Product Service: http://localhost:8082/swagger-ui.html

## 🧪 Testing

### Unit Tests

```bash
mvn test
```

### Integration Tests

```bash
mvn verify
```

Uses Testcontainers to spin up PostgreSQL, MongoDB, Kafka, etc.

### E2E Tests (Frontend)

```bash
cd frontend/shell-app
npm run test:e2e
```

## 🚢 Deployment

### Docker Build

```bash
# Build all services
docker-compose build

# Build specific service
docker-compose build user-service

# Run all services
docker-compose up -d
```

### Production Deployment

See [docs/deployment/production.md](docs/deployment/production.md) for production deployment guide.

## 📖 Additional Documentation

- [Architecture Overview](docs/architecture/overview.md)
- [Service Communication](docs/architecture/service-communication.md)
- [gRPC vs REST Decision Guide](docs/architecture/grpc-vs-rest.md)
- [Virtual Threads Guide](docs/development/virtual-threads.md)
- [Security Best Practices](docs/security/best-practices.md)
- [Troubleshooting Guide](docs/troubleshooting.md)

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📝 Development Plan

This project is being developed over 10 weeks following a detailed plan. See [plan.md](plan.md) for the complete development schedule.

Current Status: **Week 1, Day 1 - Afternoon**

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 👥 Team

- Backend Team: Java 21, Spring Boot, gRPC
- Frontend Team: React 19, Angular, Module Federation
- DevOps Team: Docker, Kubernetes, CI/CD
- QA Team: Testing, Security, Performance

## 🆘 Support

For issues and questions:
- Create an issue on GitHub
- Contact: dev@ecommerce-platform.com
- Slack: #ecommerce-platform

## 🎯 Project Goals

- ✅ Production-ready microservices architecture
- ✅ Modern frontend with micro-frontends
- ✅ High performance with gRPC and virtual threads
- ✅ Comprehensive observability
- ✅ Scalable and maintainable codebase
- ✅ Security-first approach with Auth0
- ✅ Complete test coverage (>80%)

## 🔮 Roadmap

### Week 1-2
- [x] Infrastructure setup
- [x] Common library
- [ ] Service discovery and API Gateway
- [ ] Auth0 integration

### Week 3-5
- [ ] Core services (User, Product, Cart)
- [ ] Transaction services (Order, Payment, Inventory)
- [ ] Supporting services

### Week 6-10
- [ ] Frontend development
- [ ] Testing and optimization
- [ ] Documentation
- [ ] Production deployment

---

**Built with ❤️ using Java 21, Spring Boot, React 19, and Angular**
