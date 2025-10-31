# Multi-stage build for Eureka Server
FROM maven:3.9.5-eclipse-temurin-21 AS build

WORKDIR /app

# Copy parent pom
COPY pom.xml /app/parent-pom.xml

# Copy common library
COPY common-library /app/common-library

# Copy eureka-server pom and source
COPY infrastructure/eureka-server/pom.xml /app/infrastructure/eureka-server/
COPY infrastructure/eureka-server/src /app/infrastructure/eureka-server/src

# Build the application
WORKDIR /app/infrastructure/eureka-server
RUN mvn clean package -DskipTests -f pom.xml

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

# Copy the built jar
COPY --from=build /app/infrastructure/eureka-server/target/*.jar app.jar

# Change ownership
RUN chown spring:spring app.jar

# Switch to non-root user
USER spring:spring

# Expose Eureka Server port
EXPOSE 8761

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8761/actuator/health || exit 1

# JVM options for optimal performance with virtual threads
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:+UseStringDeduplication"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
