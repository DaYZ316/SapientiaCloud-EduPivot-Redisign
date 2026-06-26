ARG JAVA_RUNTIME_IMAGE=mcr.microsoft.com/openjdk/jdk:21-ubuntu
ARG SKYWALKING_AGENT_IMAGE=apache/skywalking-java-agent:9.6.0-java21

FROM ${SKYWALKING_AGENT_IMAGE} AS skywalking-agent

FROM ${JAVA_RUNTIME_IMAGE}

WORKDIR /app

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS=""

COPY --from=skywalking-agent /skywalking/agent /skywalking/agent

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl fontconfig fonts-noto-cjk fonts-wqy-microhei libreoffice-writer \
    && fc-match "WenQuanYi Micro Hei" \
    && rm -rf /var/lib/apt/lists/*
