plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":worker"))

    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.postgres)
    implementation("io.github.oshai:kotlin-logging:7.0.14")

    // Hibernate for JSON support
    implementation("org.hibernate.orm:hibernate-core:6.5.0.Final")
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}
