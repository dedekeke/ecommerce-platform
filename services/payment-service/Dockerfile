# Simple Dockerfile for Payment Service
# Build the JAR locally first: mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Create non-root user
RUN groupadd -r paymentservice && useradd -r -g paymentservice paymentservice

# Copy the pre-built JAR file
COPY services/payment-service/target/payment-service-*.jar app.jar

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
