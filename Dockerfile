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
# Executable WAR (JSP views are only resolvable from a WAR, not a plain JAR)
COPY --from=build /app/target/fundora-fintech.war app.war
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.war"]
