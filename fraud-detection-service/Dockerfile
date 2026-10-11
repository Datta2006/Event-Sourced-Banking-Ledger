FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/fraud-detection-service-0.2.0-SNAPSHOT.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
