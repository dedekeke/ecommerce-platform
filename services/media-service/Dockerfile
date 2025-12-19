# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy parent pom first for better caching
COPY pom.xml ./
COPY services/media-service/pom.xml ./services/media-service/

# Download dependencies
RUN mvn dependency:go-offline -pl services/media-service

# Copy source code
COPY services/media-service/src ./services/media-service/src

# Build the application
RUN mvn clean package -pl services/media-service -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create storage directory
RUN mkdir -p /app/storage/thumbnails && \
    chmod -R 755 /app/storage

# Copy JAR from build stage
COPY --from=build /app/services/media-service/target/*.jar app.jar

# Expose port
EXPOSE 8089

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=5 \
  CMD wget --spider --quiet http://localhost:8089/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["--spring.profiles.active=docker"]
