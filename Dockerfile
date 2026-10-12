# =========================================================================
# Dockerfile for Fundora Social FinTech (Spring Boot + Embedded Tomcat)
# Deployable to Render, Railway, Fly.io, or AWS
# =========================================================================

# Build Stage
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Run Stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/fundora-fintech-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
