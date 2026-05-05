plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":common"))
    implementation("io.github.oshai:kotlin-logging:7.0.14")

    // Jackson for JSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
