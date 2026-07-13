# =============================================================================
# Optimized Dockerfile for Eureka Server
# Following dockerfile-checklist.md best practices
#
# Build context MUST be the repository root (see docker-compose.yml).
# =============================================================================

ARG JAVA_VERSION=21
ARG ALPINE_VERSION=3.19

# -----------------------------------------------------------------------------
# Build Stage
# -----------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-${JAVA_VERSION}-alpine AS builder

WORKDIR /build

# The root pom.xml is an aggregator: Maven parses EVERY module in its <modules>
# block to build the reactor model, even when a single module is targeted with
# `-pl ... -am`. Staging only a subset of poms makes Maven abort with
# "Child module ... does not exist", so all reactor poms are copied first
# (poms only -> maximises layer-cache reuse before sources change).
COPY pom.xml ./
COPY common-logging/pom.xml ./common-logging/
COPY common-library/pom.xml ./common-library/
COPY infrastructure/eureka-server/pom.xml ./infrastructure/eureka-server/
COPY infrastructure/config-server/pom.xml ./infrastructure/config-server/
COPY infrastructure/api-gateway/pom.xml ./infrastructure/api-gateway/
COPY services/user-service/pom.xml ./services/user-service/
COPY services/product-service/pom.xml ./services/product-service/
COPY services/cart-service/pom.xml ./services/cart-service/
COPY services/order-service/pom.xml ./services/order-service/
COPY services/payment-service/pom.xml ./services/payment-service/
COPY services/inventory-service/pom.xml ./services/inventory-service/
COPY services/notification-service/pom.xml ./services/notification-service/
COPY services/search-service/pom.xml ./services/search-service/
COPY services/media-service/pom.xml ./services/media-service/
COPY services/promotion-service/pom.xml ./services/promotion-service/
COPY services/recommendation-service/pom.xml ./services/recommendation-service/
COPY services/review-service/pom.xml ./services/review-service/

RUN mvn dependency:go-offline -pl infrastructure/eureka-server -am || true

# `-am` builds eureka-server plus its upstream reactor deps only, so only those
# modules need sources; sibling services stay pom-only in the reactor.
COPY common-logging ./common-logging
COPY common-library ./common-library
COPY infrastructure/eureka-server ./infrastructure/eureka-server

RUN mvn clean package -pl infrastructure/eureka-server -am -DskipTests && \
    mkdir -p /build/output && \
    cp infrastructure/eureka-server/target/eureka-server-*.jar /build/output/app.jar

# -----------------------------------------------------------------------------
# Runtime Stage
# -----------------------------------------------------------------------------
FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine

LABEL org.opencontainers.image.title="Eureka Server" \
      org.opencontainers.image.description="E-Commerce Service Discovery Server" \
      org.opencontainers.image.authors="E-Commerce Platform Team" \
      org.opencontainers.image.source="https://github.com/ecommerce-platform" \
      org.opencontainers.image.version="1.0.0" \
      org.opencontainers.image.vendor="E-Commerce Platform"

RUN addgroup -S appgroup && adduser -S appuser -G appgroup && \
    mkdir -p /app && chown -R appuser:appgroup /app

WORKDIR /app

COPY --from=builder --chown=appuser:appgroup /build/output/app.jar ./app.jar

USER appuser

EXPOSE 8761

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8761/actuator/health || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+AlwaysPreTouch -XX:+UseStringDeduplication -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
