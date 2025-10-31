# Multi-stage build for Config Server
FROM maven:3.9.5-eclipse-temurin-21 AS build

WORKDIR /app

# Copy parent pom
COPY pom.xml /app/parent-pom.xml

# Copy common library
COPY common-library /app/common-library

# Copy config-server pom and source
COPY infrastructure/config-server/pom.xml /app/infrastructure/config-server/
COPY infrastructure/config-server/src /app/infrastructure/config-server/src

# Build the application
WORKDIR /app/infrastructure/config-server
RUN mvn clean package -DskipTests -f pom.xml

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Install git (required for Git backend)
RUN apk add --no-cache git

# Create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

# Copy the built jar
COPY --from=build /app/infrastructure/config-server/target/*.jar app.jar

# Copy configuration repository
COPY config-repo /config-repo

# Change ownership
RUN chown -R spring:spring /app /config-repo

# Switch to non-root user
USER spring:spring

# Expose Config Server port
EXPOSE 8888

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8888/actuator/health || exit 1

# JVM options for optimal performance with virtual threads
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:+UseStringDeduplication"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
