FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY services/promotion-service/target/promotion-service-*.jar app.jar

EXPOSE 8090

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+AlwaysPreTouch"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
