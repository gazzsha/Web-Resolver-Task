plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":sandbox"))
    implementation(project(":ai-analyzer"))
    implementation(project(":scenario-runner"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.kafka)
    implementation("io.github.oshai:kotlin-logging:7.0.14")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")

    // Micrometer custom metrics for /actuator/prometheus
    implementation(libs.micrometer.core)
    implementation(libs.spring.boot.starter.actuator)

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("io.mockk:mockk:1.13.13")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
