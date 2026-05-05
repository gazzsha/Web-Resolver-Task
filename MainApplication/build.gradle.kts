plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgresql)
    implementation(project(":common"))
    implementation(project(":api-generator"))
    implementation(project(":task-resolver"))
    implementation(project(":db"))
    implementation(project(":sandbox"))
    implementation(project(":worker"))
    implementation(project(":ai-analyzer"))
    implementation(project(":scenario-runner"))
}
