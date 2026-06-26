ARG MAVEN_IMAGE=mcr.microsoft.com/openjdk/jdk:21-ubuntu
ARG RUNTIME_IMAGE=mcr.microsoft.com/openjdk/jdk:21-ubuntu
ARG SKYWALKING_AGENT_IMAGE=apache/skywalking-java-agent:9.6.0-java21

FROM ${MAVEN_IMAGE} AS build

WORKDIR /workspace

RUN apt-get update \
    && apt-get install -y --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/*

COPY . .

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests -pl sc-notification -am package

FROM ${SKYWALKING_AGENT_IMAGE} AS skywalking-agent

FROM ${RUNTIME_IMAGE}

WORKDIR /app

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS=""

COPY --from=build /workspace/sc-notification/target/sc-notification-*.jar app.jar
COPY --from=skywalking-agent /skywalking/agent /skywalking/agent

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

EXPOSE 28082

HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=40s \
  CMD curl -fsS http://localhost:28082/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
