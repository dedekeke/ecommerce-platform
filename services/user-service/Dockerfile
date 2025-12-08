# Simple Dockerfile for User Service
# Build the JAR locally first: mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Create non-root user
RUN groupadd -r userservice && useradd -r -g userservice userservice

# Copy the pre-built JAR file
COPY services/user-service/target/user-service-*.jar app.jar

# Change ownership
RUN chown -R userservice:userservice /app

# Switch to non-root user
USER userservice

# Expose the application port
EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

# JVM options
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
