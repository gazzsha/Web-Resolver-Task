FROM docker-proxy.artifactory.tcsbank.ru/eclipse-temurin:21-jdk-alpine

WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle/catalog-version gradle/catalog-version

# Copy main application
COPY MainApplication MainApplication
COPY common common
COPY api-generator api-generator
COPY task-resolver task-resolver
COPY task-process task-process
COPY db db
COPY worker worker
COPY sandbox sandbox
COPY ai-analyzer ai-analyzer
COPY scenario-runner scenario-runner

# Build the application
RUN chmod +x ./gradlew
RUN ./gradlew :MainApplication:bootJar --no-daemon

# Run the application
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "MainApplication/build/libs/MainApplication.jar"]
