# =============================================================================
# Optimized Dockerfile for Payment Service
# Following dockerfile-checklist.md best practices
# =============================================================================

ARG JAVA_VERSION=21
ARG ALPINE_VERSION=3.19

# -----------------------------------------------------------------------------
# Build Stage
# -----------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-${JAVA_VERSION}-alpine AS builder

WORKDIR /build

COPY pom.xml ./
COPY common-library/pom.xml ./common-library/
COPY services/payment-service/pom.xml ./services/payment-service/

RUN mvn dependency:go-offline -pl services/payment-service -am || true

COPY common-library ./common-library
COPY services/payment-service ./services/payment-service

RUN mvn clean package -pl services/payment-service -am -DskipTests && \
    mkdir -p /build/output && \
    cp services/payment-service/target/payment-service-*.jar /build/output/app.jar

# -----------------------------------------------------------------------------
# Runtime Stage
# -----------------------------------------------------------------------------
FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine

LABEL org.opencontainers.image.title="Payment Service" \
      org.opencontainers.image.description="E-Commerce Payment Processing Service" \
      org.opencontainers.image.authors="E-Commerce Platform Team" \
      org.opencontainers.image.source="https://github.com/ecommerce-platform" \
      org.opencontainers.image.version="1.0.0" \
      org.opencontainers.image.vendor="E-Commerce Platform"

RUN addgroup -S appgroup && adduser -S appuser -G appgroup && \
    mkdir -p /app && chown -R appuser:appgroup /app

WORKDIR /app

COPY --from=builder --chown=appuser:appgroup /build/output/app.jar ./app.jar

USER appuser

EXPOSE 8085 9092

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8085/actuator/health || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+AlwaysPreTouch -XX:+UseStringDeduplication -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
