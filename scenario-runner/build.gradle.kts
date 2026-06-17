plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":sandbox"))
    implementation("io.github.oshai:kotlin-logging:7.0.14")
}
