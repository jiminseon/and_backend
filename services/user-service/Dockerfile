FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system spring \
    && useradd --system --gid spring spring
WORKDIR /app

ARG JAR_FILE=build/libs/user-module-0.0.1-SNAPSHOT.jar
COPY --chown=spring:spring ${JAR_FILE} app.jar

USER spring:spring
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
