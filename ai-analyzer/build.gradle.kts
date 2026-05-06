plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":sandbox"))
    implementation("io.github.oshai:kotlin-logging:7.0.14")

    // GigaChat HTTP client (Spring WebClient + Reactor Netty)
    implementation("org.springframework.boot:spring-boot-starter-webflux:3.5.0")

    // Caffeine in-memory cache for AI analysis results
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // Jackson for JSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")

    // Optional: AST parsing for code analysis
    implementation("com.github.javaparser:javaparser-core:3.26.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test:3.5.0")
    testImplementation("io.mockk:mockk:1.13.13")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
