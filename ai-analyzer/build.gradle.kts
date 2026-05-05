plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":sandbox"))
    implementation("io.github.oshai:kotlin-logging:7.0.14")

    // ChatGPT for AI analysis
    implementation(libs.chatgpt)

    // Jackson for JSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")

    // Optional: AST parsing for code analysis
    // For Java: JavaParser
    implementation("com.github.javaparser:javaparser-core:3.26.2")
}
