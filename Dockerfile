FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN sh ./gradlew --no-daemon bootJar

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --system waterapi && useradd --system --gid waterapi waterapi \
    && mkdir /app/data && chown waterapi:waterapi /app/data
COPY --from=build --chown=waterapi:waterapi /workspace/build/libs/waterapi.jar /app/app.jar
USER waterapi
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
