# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY infra/eureka/pom.xml ./infra/eureka/pom.xml
COPY infra/eureka/src ./infra/eureka/src
RUN mvn -f infra/eureka/pom.xml clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/infra/eureka/target/eureka-server-0.2.0-SNAPSHOT.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]
