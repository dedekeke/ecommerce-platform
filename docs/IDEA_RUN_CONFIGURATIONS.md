# IntelliJ IDEA Run Configurations Guide

> Back to [README](../README.md).

This guide explains how to set up run configurations in IntelliJ IDEA for debugging microservices in the e-commerce platform.

## Prerequisites

1. **IntelliJ IDEA Ultimate** (required for Spring Boot support)
2. **Maven Integration** plugin (usually pre-installed)
3. **Spring Boot** plugin (usually pre-installed)
4. **Local infrastructure** running (PostgreSQL, MySQL, Redis, Kafka)

## Quick Setup

### 1. Import Project

1. Open IntelliJ IDEA
2. Click **File → Open**
3. Navigate to the project root directory
4. Select the `pom.xml` file
5. Click **Open as Project**
6. Wait for Maven to import all dependencies

### 2. Enable Annotation Processing

1. Go to **File → Settings** (macOS: **IntelliJ IDEA → Preferences**)
2. Navigate to **Build, Execution, Deployment → Compiler → Annotation Processors**
3. Check **Enable annotation processing**
4. Click **OK**

## Creating Run Configurations

### Method 1: Automatic Configuration (Recommended)

IntelliJ IDEA can auto-detect Spring Boot applications:

1. Open the main application class (e.g., `CartServiceApplication.java`)
2. Look for a green play icon (▶) in the left gutter next to the `main` method
3. Right-click the icon and select **Modify Run Configuration...**
4. Configure as shown below in the **Manual Configuration** section
5. Click **OK**

### Method 2: Manual Configuration

#### Cart Service Configuration

1. Go to **Run → Edit Configurations...**
2. Click the **+** button (top-left) and select **Spring Boot**
3. Configure the following:

**General Tab:**
```
Name: Cart Service [local]
Main class: com.ecommerce.cartservice.CartServiceApplication
Module: cart-service
```

**Spring Boot Tab:**
```
Active profiles: local
```

**Configuration Tab (VM Options):**
```
-Xmx512m
-Xms256m
-Dspring.profiles.active=local
-DSECURITY_ENABLED=false
-Dlogging.level.com.ecommerce.cartservice=DEBUG
-Dspring.jpa.show-sql=true
```

**Environment Variables:**
```
SECURITY_ENABLED=false
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
AUTH0_DOMAIN=
AUTH0_AUDIENCE=
```

**Working Directory:**
```
$MODULE_WORKING_DIR$
```

4. Click **OK** to save

#### User Service Configuration

Follow the same steps as Cart Service with these changes:

```
Name: User Service [local]
Main class: com.ecommerce.userservice.UserServiceApplication
Module: user-service
```

**VM Options:**
```
-Xmx512m
-Xms256m
-Dspring.profiles.active=local
-DSECURITY_ENABLED=false
-Dserver.port=8088
```

#### Product Service Configuration

```
Name: Product Service [local]
Main class: com.ecommerce.productservice.ProductServiceApplication
Module: product-service
```

**VM Options:**
```
-Xmx512m
-Xms256m
-Dspring.profiles.active=local
-DSECURITY_ENABLED=false
-Dserver.port=8082
```

#### Eureka Server Configuration

```
Name: Eureka Server [local]
Main class: com.ecommerce.eurekaserver.EurekaServerApplication
Module: eureka-server
```

**VM Options:**
```
-Xmx256m
-Xms128m
-Dserver.port=8761
```

#### API Gateway Configuration

```
Name: API Gateway [local]
Main class: com.ecommerce.apigateway.ApiGatewayApplication
Module: api-gateway
```

**VM Options:**
```
-Xmx512m
-Xms256m
-Dspring.profiles.active=local
-DSECURITY_ENABLED=false
-Dserver.port=8080
```

## Compound Run Configuration (Run Multiple Services)

To run multiple services together:

1. Go to **Run → Edit Configurations...**
2. Click **+** → **Compound**
3. Name it: **All Services [local]**
4. Click **+** (in the dialog) and add configurations in this order:
   - Eureka Server [local]
   - User Service [local]
   - Product Service [local]
   - Cart Service [local]
   - API Gateway [local]
5. Check **Run in parallel** for faster startup
6. Click **OK**

## Debugging Configuration

### Standard Debug Configuration

All the configurations above can be run in debug mode:

1. Set breakpoints by clicking in the left gutter of the code editor
2. Click the **Debug** button (🐛) instead of **Run**
3. Or use **Run → Debug 'Service Name'**

### Remote Debug Configuration

For debugging services running in Docker:

1. **Run → Edit Configurations...**
2. Click **+** → **Remote JVM Debug**
3. Configure:

```
Name: Cart Service [Remote]
Debugger mode: Attach to remote JVM
Host: localhost
Port: 5005
```

4. Add to your Docker service in `docker-compose.yml`:

```yaml
cart-service:
  environment:
    JAVA_TOOL_OPTIONS: >
      -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
  ports:
    - "5005:5005"
```

5. Start the Docker service
6. Run the remote debug configuration in IntelliJ

## Advanced VM Options

### For Production-Like Testing

```
-Xmx1024m
-Xms512m
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/tmp/heapdump.hprof
-Dspring.profiles.active=local
-DSECURITY_ENABLED=true
```

### For Memory Profiling

```
-Xmx512m
-Xms256m
-XX:+UnlockExperimentalVMOptions
-XX:+UseCGroupMemoryLimitForHeap
-XX:NativeMemoryTracking=detail
-Dspring.profiles.active=local
```

### For Performance Profiling

```
-Xmx512m
-Xms256m
-XX:+FlightRecorder
-XX:StartFlightRecording=duration=60s,filename=/tmp/recording.jfr
-Dspring.profiles.active=local
```

## Common VM Options Explained

| Option | Description |
|--------|-------------|
| `-Xmx512m` | Maximum heap memory (512 MB) |
| `-Xms256m` | Initial heap memory (256 MB) |
| `-Dspring.profiles.active=local` | Active Spring profile |
| `-DSECURITY_ENABLED=false` | Disable Auth0 security |
| `-Dserver.port=8083` | Override server port |
| `-Dlogging.level.root=INFO` | Set root log level |
| `-Dspring.jpa.show-sql=true` | Show SQL queries |

## Environment Variables

### Required for Local Development

```
SECURITY_ENABLED=false
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
MYSQL_USER=admin
MYSQL_PASSWORD=admin123
AUTH0_DOMAIN=
AUTH0_AUDIENCE=
```

### Optional

```
REDIS_HOST=localhost
REDIS_PORT=6379
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
EUREKA_SERVER_URL=http://localhost:8761/eureka/
```

## Troubleshooting

### Port Already in Use

**Error:** `Port 8083 is already in use`

**Solution:**
1. Stop the service using the port: `lsof -ti:8083 | xargs kill -9`
2. Or change the port in VM options: `-Dserver.port=8084`

### Out of Memory Error

**Error:** `java.lang.OutOfMemoryError: Java heap space`

**Solution:**
1. Increase heap memory in VM options: `-Xmx1024m`
2. Enable heap dump: `-XX:+HeapDumpOnOutOfMemoryError`

### Database Connection Failed

**Error:** `Connection to localhost:5432 refused`

**Solution:**
1. Start infrastructure: `./scripts/setup-local-dev.sh`
2. Verify PostgreSQL is running: `pg_isready -h localhost -p 5432`
3. Check database exists: `psql -h localhost -U postgres -l`

### Auth0 JWT Validation Failed

**Error:** `Invalid issuer`

**Solution:**
1. Set `SECURITY_ENABLED=false` in environment variables
2. Or configure correct Auth0 settings in `.env`

### Annotation Processing Not Working

**Error:** Lombok getters/setters not found

**Solution:**
1. Enable annotation processing: **Settings → Compiler → Annotation Processors**
2. Install Lombok plugin if not already installed
3. Rebuild project: **Build → Rebuild Project**

## Hot Reload Configuration

For faster development with automatic reload:

1. Add Spring Boot DevTools dependency (already included)
2. **Settings → Build, Execution, Deployment → Compiler**
3. Check **Build project automatically**
4. **Settings → Advanced Settings**
5. Check **Allow auto-make to start even if developed application is currently running**
6. Restart IntelliJ IDEA

Now changes to Java files will trigger automatic restart.

## Useful Shortcuts

| Action | macOS | Windows/Linux |
|--------|-------|---------------|
| Run | Ctrl + R | Shift + F10 |
| Debug | Ctrl + D | Shift + F9 |
| Stop | Cmd + F2 | Ctrl + F2 |
| Edit Configurations | Cmd + Alt + R | Ctrl + Alt + R |
| Toggle Breakpoint | Cmd + F8 | Ctrl + F8 |
| Step Over | F8 | F8 |
| Step Into | F7 | F7 |
| Resume | Cmd + Alt + R | F9 |

## Service Startup Order

For optimal startup, run services in this order:

1. **Eureka Server** (8761) - Service discovery
2. **Config Server** (8888) - Configuration management (optional)
3. **User Service** (8088) - User management
4. **Product Service** (8082) - Product catalog
5. **Cart Service** (8083) - Shopping cart
6. **API Gateway** (8080) - API routing

Wait ~10 seconds between each service for proper registration.

## Monitoring & Debugging

### Actuator Endpoints

All services expose actuator endpoints:

- Health: http://localhost:8083/actuator/health
- Metrics: http://localhost:8083/actuator/metrics
- Info: http://localhost:8083/actuator/info

### Swagger UI

API documentation available at:

- Cart Service: http://localhost:8083/swagger-ui.html
- User Service: http://localhost:8088/swagger-ui.html
- Product Service: http://localhost:8082/swagger-ui.html

### Eureka Dashboard

Service registry: http://localhost:8761

## Additional Resources

- [IntelliJ IDEA Spring Boot Guide](https://www.jetbrains.com/help/idea/spring-boot.html)
- [IntelliJ IDEA Debugging](https://www.jetbrains.com/help/idea/debugging-code.html)
- [Spring Boot DevTools](https://docs.spring.io/spring-boot/docs/current/reference/html/using.html#using.devtools)
- [Java Flight Recorder](https://docs.oracle.com/javacomponents/jmc-5-4/jfr-runtime-guide/about.htm)
