# Custom Kotlin sandbox image — public zenika/kotlin is stuck at 1.4 which
# doesn't support modern syntax (`..<` rangeUntil, etc.). Build once with:
#   docker build --platform=linux/amd64 -t web-resolver/kotlin:2.0.21 \
#     sandbox/docker/kotlin/
# The SandboxImageManager prewarm step will skip this image if it's already
# cached locally; the user has to build it once on a fresh machine.

FROM --platform=linux/amd64 eclipse-temurin:21-jdk-alpine

ARG KOTLIN_VERSION=1.9.22

# bash is required at runtime: kotlinc's launcher script starts with
# `#!/usr/bin/env bash`, alpine ships only ash.
RUN apk add --no-cache bash curl unzip \
 && curl -fsSL "https://github.com/JetBrains/kotlin/releases/download/v${KOTLIN_VERSION}/kotlin-compiler-${KOTLIN_VERSION}.zip" -o /tmp/kotlin.zip \
 && unzip -q /tmp/kotlin.zip -d /opt \
 && rm /tmp/kotlin.zip \
 && apk del curl unzip

ENV PATH="/opt/kotlinc/bin:${PATH}"

RUN kotlinc -version 2>&1 | head -1
