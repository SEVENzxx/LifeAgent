FROM maven:3.9.9-eclipse-temurin-17-alpine AS build
WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S lifeagent && adduser -S lifeagent -G lifeagent
COPY --from=build /workspace/target/lifeagent.jar /app/lifeagent.jar

USER lifeagent
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "/app/lifeagent.jar"]
