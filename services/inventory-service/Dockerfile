# Simple Dockerfile for Inventory Service
# Build the JAR locally first: mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Create non-root user
RUN groupadd -r inventoryservice && useradd -r -g inventoryservice inventoryservice

# Copy the pre-built JAR file
COPY services/inventory-service/target/inventory-service-*.jar app.jar

# Change ownership
RUN chown -R inventoryservice:inventoryservice /app

# Switch to non-root user
USER inventoryservice

# Expose ports (HTTP: 8086, gRPC: 9093)
EXPOSE 8086 9093

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8086/actuator/health || exit 1

# JVM options for production
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
