ARG MAVEN_IMAGE=mcr.microsoft.com/openjdk/jdk:21-ubuntu
ARG RUNTIME_IMAGE=mcr.microsoft.com/openjdk/jdk:21-ubuntu

FROM ${MAVEN_IMAGE} AS build

WORKDIR /workspace

RUN apt-get update \
    && apt-get install -y --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/*

COPY . .

RUN mvn -B -ntp -DskipTests clean install -pl sc-notification -am

FROM ${RUNTIME_IMAGE}

WORKDIR /app

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS=""

COPY --from=build /workspace/sc-notification/target/sc-notification-*.jar app.jar

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

EXPOSE 28082

HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=40s \
  CMD curl -fsS http://localhost:28082/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
