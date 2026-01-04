plugins {
    id("org.openapi.generator") version "7.17.0"
}


dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.data.jpa)

    implementation(libs.jackson.core)
    implementation(libs.jackson.databind.nullable)

    implementation(libs.swagger.annotations.jakarta)
    implementation(libs.jakarta.annotation.api)
    implementation(libs.jakarta.validation.api)

    implementation(libs.springdoc.openapi.starter.webmvc.ui)
    implementation(libs.springdoc.openapi.starter.webmvc.api)
}


val customCommonAdditionalProperties = mapOf(
    "dateLibrary" to "java8",
    "java8" to "true",
    "oas3" to "true",
    "delegatePattern" to "false",
    "interfaceOnly" to "true",
    "useBeanValidation" to "true",
    "useTags" to "true",
    "generateApiTests" to "false",
    "generateApiDocumentation" to "false",
    "supportingFiles" to "false",
    "exceptionHandler" to "false",
    "skipDefaultInterface" to "true",
    "serializableModel" to "false",
    "sourceFolder" to "",
    "hideGenerationTimestamp" to "true",
    "jakarta" to "true",
    "useSpringBoot3" to "true",
    "useJakartaEe" to "true",
    "validationMode" to "legacy"
)


val resourcesDir = "$projectDir/resources"
val generatedDir = "$buildDir/generated/openapi/"

val openApiFilesGroupByProcess = mapOf(
    "task-resolver-api.yml" to "TaskResolver"
)

val generatorTaskNames = openApiFilesGroupByProcess.map { registerGenerateTask(it.key, it.value) }

tasks.compileKotlin {
    dependsOn(generatorTaskNames)
}

fun registerGenerateTask(filePath: String, process: String): String {
    val taskName = "generateApi$process"
    tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>(taskName) {
        group = "openapi tools"
        generatorName = "spring"
        inputSpec = "$resourcesDir/api/$filePath"
        outputDir = generatedDir
        apiPackage = "web"
        invokerPackage = "ru"
        modelPackage = "model"
        configOptions.set(mapOf(
            "useSpringBoot3" to "true",
            "interfaceOnly" to "true",
            "serializationLibrary" to "jackson"
        ))
        skipValidateSpec = true
        logToStderr = true
        generateAliasAsModel = false
        enablePostProcessFile = false
        additionalProperties.set(customCommonAdditionalProperties)
    }
    return taskName
}


sourceSets.main {
    java.srcDirs(generatedDir)
}
