# Multi-stage build for API Gateway
FROM maven:3.9.5-eclipse-temurin-21 AS build

WORKDIR /app

# Copy parent pom
COPY pom.xml /app/pom.xml

# Copy common library
COPY common-library /app/common-library

# Copy api-gateway pom and source
COPY infrastructure/api-gateway/pom.xml /app/infrastructure/api-gateway/
COPY infrastructure/api-gateway/src /app/infrastructure/api-gateway/src

# Build the application
WORKDIR /app/infrastructure/api-gateway
RUN mvn clean package -DskipTests -f pom.xml

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy the built JAR
COPY --from=build /app/infrastructure/api-gateway/target/*.jar app.jar

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
