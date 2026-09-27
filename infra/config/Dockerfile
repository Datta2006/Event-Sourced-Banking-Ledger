FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/config-server-0.2.0-SNAPSHOT.jar app.jar
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "app.jar"]
