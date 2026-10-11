FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/loan-service-0.2.0-SNAPSHOT.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
