FROM maven:3.9.9-eclipse-temurin-17

WORKDIR /app

COPY . .

RUN mvn clean package

CMD ["java", "-jar", "target/healthCheck-1.0-SNAPSHOT.jar"]