FROM eclipse-temurin:25-jdk-alpine as builder

WORKDIR /app

COPY pom.xml .
COPY .mvn .mvn
COPY src src

RUN apk add --no-cache maven && \
    mvn clean package -DskipTests && \
    apk del maven

FROM eclipse-temurin:25-jdk-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
EXPOSE 8080

