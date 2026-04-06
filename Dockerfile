FROM maven:3.9.9-eclipse-temurin-17

WORKDIR /app

COPY src/main/java/org/example .

RUN mvn clean package

EXPOSE 8080

CMD ["java","-cp","target/classes:target/dependency/*","org.example.Main"]