FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the JAR file
COPY target/*.jar app.jar

# Expose port
EXPOSE 8088

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
