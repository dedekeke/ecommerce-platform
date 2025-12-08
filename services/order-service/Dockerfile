# Simple Dockerfile for Order Service
# Build the JAR locally first: mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Create non-root user
RUN groupadd -r orderservice && useradd -r -g orderservice orderservice

# Copy the pre-built JAR file
COPY services/order-service/target/order-service-*.jar app.jar

# Change ownership
RUN chown -R orderservice:orderservice /app

# Switch to non-root user
USER orderservice

# Expose ports (HTTP: 8084, gRPC: 9094)
EXPOSE 8084 9094

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8084/actuator/health || exit 1

# JVM options for production
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
