# Simple Dockerfile for Product Service
# Build the JAR locally first: mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Create non-root user
RUN groupadd -r productservice && useradd -r -g productservice productservice

# Copy the pre-built JAR file
COPY services/product-service/target/product-service-*.jar app.jar

# Change ownership
RUN chown -R productservice:productservice /app

# Switch to non-root user
USER productservice

# Expose port
EXPOSE 8082

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8082/actuator/health || exit 1

# JVM options
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
