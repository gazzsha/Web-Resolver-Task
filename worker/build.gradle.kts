plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    implementation(project(":common"))
    implementation(project(":sandbox"))
    implementation(project(":ai-analyzer"))
    implementation(project(":scenario-runner"))
    
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.kafka)
    implementation("io.github.oshai:kotlin-logging:7.0.14")
    
    // Jackson for JSON serialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")
}
