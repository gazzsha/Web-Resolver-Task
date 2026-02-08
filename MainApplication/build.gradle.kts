plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(project(":common"))
    implementation(project(":api-generator"))
    implementation(project(":task-resolver"))
    implementation(project(":task-process"))
    implementation(project(":db"))
}
