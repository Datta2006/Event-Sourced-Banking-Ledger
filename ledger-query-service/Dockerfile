FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/ledger-query-service-0.2.0-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
