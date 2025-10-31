# Multi-stage build for API Gateway
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

# Copy parent POM and common library
COPY pom.xml .
COPY common-library ./common-library

# Copy API Gateway source
COPY infrastructure/api-gateway ./infrastructure/api-gateway

# Build the application
RUN apk add --no-cache maven && \
    mvn clean package -pl infrastructure/api-gateway -am -DskipTests && \
    mkdir -p target && \
    cp infrastructure/api-gateway/target/*.jar target/api-gateway.jar

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy the built JAR
COPY --from=build /workspace/target/api-gateway.jar app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=40s \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the application with virtual threads enabled
ENTRYPOINT ["java", \
    "-XX:+UseZGC", \
    "-XX:+ZGenerational", \
    "-Xms256m", \
    "-Xmx512m", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", \
    "app.jar"]
