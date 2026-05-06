plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgresql)

    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    implementation(project(":common"))
    implementation(project(":api-generator"))
    implementation(project(":task-resolver"))
    implementation(project(":db"))
    implementation(project(":sandbox"))
    implementation(project(":worker"))
    implementation(project(":ai-analyzer"))
    implementation(project(":scenario-runner"))

    implementation(libs.logger)

    testImplementation("org.springframework.boot:spring-boot-starter-test:3.5.0") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.20.4"))
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Docker Desktop on macOS exposes the daemon via ~/.docker/run/docker.sock
    val home = System.getProperty("user.home")
    val macSock = file("$home/.docker/run/docker.sock")
    if (macSock.exists()) {
        environment("DOCKER_HOST", "unix://${macSock.absolutePath}")
        environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
    }
}
