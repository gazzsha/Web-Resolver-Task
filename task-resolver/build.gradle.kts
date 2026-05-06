plugins {
    alias(libs.plugins.kotlin.spring)
    kotlin("plugin.jpa") version "2.2.21"
}

dependencies {
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(libs.postgres)
    implementation(libs.spring.kafka)
    implementation(project(":api-generator"))
    implementation(project(":common"))
    implementation(project(":db"))
    implementation(project(":ai-analyzer"))
    implementation(project(":sandbox"))
    implementation(project(":scenario-runner"))
    implementation(project(":worker"))
    implementation(libs.logger)



    testImplementation(libs.spring.boot.starter.data.jpa)
    testImplementation(libs.spring.boot.starter.web)
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
