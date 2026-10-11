FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/trust-score-service-0.2.0-SNAPSHOT.jar app.jar
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "app.jar"]
