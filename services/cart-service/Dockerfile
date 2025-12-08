# Simple Dockerfile for Cart Service
# Build the JAR locally first: mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Create non-root user
RUN groupadd -r cartservice && useradd -r -g cartservice cartservice

# Copy the pre-built JAR file
COPY services/cart-service/target/cart-service-*.jar app.jar

# Change ownership
RUN chown -R cartservice:cartservice /app

# Switch to non-root user
USER cartservice

# Expose ports (HTTP: 8083, gRPC: 9090)
EXPOSE 8083 9090

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8083/actuator/health || exit 1

# JVM options
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
