# Multi-stage build for Order Service

# Stage 1: Build
FROM maven:3.9.5-eclipse-temurin-21 AS build
WORKDIR /app

# Copy parent POM and common library
COPY pom.xml .
COPY common-library common-library/

# Copy Order Service
COPY services/order-service services/order-service/

# Build the application (skip tests for faster builds)
RUN mvn clean package -pl services/order-service -am -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Create non-root user
RUN groupadd -r orderservice && useradd -r -g orderservice orderservice

# Copy JAR from build stage
COPY --from=build /app/services/order-service/target/*.jar app.jar

# Change ownership
RUN chown -R orderservice:orderservice /app

# Switch to non-root user
USER orderservice

# Expose ports
EXPOSE 8084 9091

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8084/actuator/health || exit 1

# JVM options for production
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
