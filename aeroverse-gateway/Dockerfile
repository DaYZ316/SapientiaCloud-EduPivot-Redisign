ARG MAVEN_IMAGE=mcr.microsoft.com/openjdk/jdk:21-ubuntu
ARG RUNTIME_IMAGE=mcr.microsoft.com/openjdk/jdk:21-ubuntu

FROM ${MAVEN_IMAGE} AS build

WORKDIR /workspace

RUN apt-get update \
    && apt-get install -y --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/*

COPY . .

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests -pl aeroverse-gateway -am package

FROM ${RUNTIME_IMAGE}

WORKDIR /app

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS=""

COPY --from=build /workspace/aeroverse-gateway/target/aeroverse-gateway-*.jar app.jar

EXPOSE 39080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
