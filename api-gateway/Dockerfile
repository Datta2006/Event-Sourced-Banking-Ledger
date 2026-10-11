FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/api-gateway-0.2.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
