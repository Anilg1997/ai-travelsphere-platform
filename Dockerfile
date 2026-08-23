FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /workspace
COPY . .

ARG SERVICE
RUN test -n "$SERVICE" && mvn -pl "backend/${SERVICE}" -am clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
ARG SERVICE
COPY --from=builder /workspace/backend/${SERVICE}/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
