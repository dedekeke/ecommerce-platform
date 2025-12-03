# Multi-stage build for Payment Service

# Stage 1: Build
FROM maven:3.9.5-eclipse-temurin-21 AS build
WORKDIR /app

# Copy parent POM
COPY pom.xml .

# Copy Payment Service
COPY services/payment-service services/payment-service/

# Build the application (skip tests for faster builds)
RUN mvn clean package -pl services/payment-service -am -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Create non-root user
RUN groupadd -r paymentservice && useradd -r -g paymentservice paymentservice

# Copy JAR from build stage
COPY --from=build /app/services/payment-service/target/*.jar app.jar

# Change ownership
RUN chown -R paymentservice:paymentservice /app

# Switch to non-root user
USER paymentservice

# Expose ports (HTTP: 8085, gRPC: 9092)
EXPOSE 8085 9092

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8085/actuator/health || exit 1

# JVM options for production
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
