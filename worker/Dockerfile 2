FROM docker-proxy.artifactory.tcsbank.ru/eclipse-temurin:21-jdk-alpine

WORKDIR /app


# Copy Gradle wrapper and build files
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle/catalog-version gradle/catalog-version

# Copy worker and dependencies
COPY common common
COPY sandbox sandbox
COPY ai-analyzer ai-analyzer
COPY worker worker
COPY scenario-runner scenario-runner

# Build the worker application
RUN chmod +x ./gradlew
RUN ./gradlew :worker:bootJar --no-daemon

# Create temp directory for sandbox
RUN mkdir -p /tmp/web-resolver-sandbox

# Run the worker
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "worker/build/libs/worker.jar"]
