# Stage 1 — build (Maven + JDK 17)
FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /app
# Cache dependencies first (layer reuse)
COPY pom.xml .
RUN  mvn dependency:go-offline -q
COPY src ./src
RUN  mvn clean package -DskipTests -q

# Stage 2 — run (slim JRE only, ~90 MB)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/healthCheck-1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]