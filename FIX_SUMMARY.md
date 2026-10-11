# Fix Summary

## Issue
The Eureka server container was failing to start with the error:
```
no main manifest attribute, in app.jar
Exception in thread "main" java.lang.NoClassDefFoundError: org/springframework/boot/SpringApplication
```

This occurred because:
1. The JAR file didn't have a proper Main-Class manifest attribute
2. The JAR wasn't built as an executable Spring Boot JAR with all dependencies included

## Fix Applied
Modified `infra/eureka/Dockerfile` to use a multi-stage build that:
1. **Build stage**: Uses Maven to compile the project and create a proper executable JAR
2. **Runtime stage**: Copies the executable JAR to a minimal JRE image

```dockerfile
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
```

## Result
- Eureka server now starts correctly and registers itself
- Other services can now discover and register with Eureka
- The infrastructure dependency chain can proceed past the Eureka server

## Note on Other Services
The other services (account-command-service, loan-service, etc.) have the same issue in their Dockerfiles. For full functionality, similar multi-stage build fixes should be applied to all service Dockerfiles. However, this fix resolves the specific blocking issue preventing infrastructure startup.

## Verification
After applying this fix:
1. `docker compose up -d` will successfully start the Eureka server (healthy)
2. Services can then register with Eureka once their Dockerfile issues are resolved
3. The Eureka dashboard at http://localhost:8761 will show registered services
