# Simple Dockerfile for Notification Service
# Build the JAR locally first: mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Create non-root user
RUN groupadd -r notificationservice && useradd -r -g notificationservice notificationservice

# Copy the pre-built JAR file
COPY services/notification-service/target/notification-service-*.jar app.jar

# Change ownership
RUN chown -R notificationservice:notificationservice /app

# Switch to non-root user
USER notificationservice

# Expose the application port
EXPOSE 8087

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8087/actuator/health || exit 1

# JVM options
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
