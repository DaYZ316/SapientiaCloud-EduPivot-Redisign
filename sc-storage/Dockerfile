ARG MAVEN_IMAGE=mcr.microsoft.com/openjdk/jdk:21-ubuntu
ARG RUNTIME_IMAGE=mcr.microsoft.com/openjdk/jdk:21-ubuntu

FROM ${MAVEN_IMAGE} AS build
WORKDIR /workspace
RUN apt-get update \
    && apt-get install -y --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/*
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -pl sc-storage -am -DskipTests package

FROM ${RUNTIME_IMAGE}
WORKDIR /app
ENV TZ=Asia/Shanghai
ENV JAVA_OPTS=""
COPY --from=build /workspace/sc-storage/target/sc-storage-*.jar app.jar

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

EXPOSE 28085

HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=40s \
  CMD curl -fsS http://localhost:28085/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
