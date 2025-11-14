# Product Service Dockerfile
FROM eclipse-temurin:21-jre-alpine

# Set working directory
WORKDIR /app

# Copy the JAR file
COPY services/product-service/target/product-service-1.0.0-SNAPSHOT.jar app.jar

# Create a non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Expose port
EXPOSE 8082

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8082/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
