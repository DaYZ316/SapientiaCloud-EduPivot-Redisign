ARG JAVA_RUNTIME_IMAGE=mcr.microsoft.com/openjdk/jdk:21-ubuntu

FROM ${JAVA_RUNTIME_IMAGE}

WORKDIR /app

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS=""

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl fontconfig fonts-noto-cjk fonts-wqy-microhei libreoffice-writer \
    && fc-match "WenQuanYi Micro Hei" \
    && rm -rf /var/lib/apt/lists/*
