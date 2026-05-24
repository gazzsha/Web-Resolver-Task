package ru.sandbox.service

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

class SandboxImageManager {

    private val knownImages = setOf(
        // DockerSandboxService supports java + python + kotlin.
        // Kotlin uses a custom image built from sandbox/docker/kotlin/Dockerfile (Kotlin 1.9.22 on JDK 21).
        // ensureImage() will skip the pull for the local-only kotlin tag and rely on the local cache.
        "eclipse-temurin:21-jdk-alpine",
        "python:3.11-alpine",
        "web-resolver/kotlin:1.9.22"
    )

    /**
     * Pulls all known images sequentially. Blocking — call from a non-startup
     * thread (e.g. ApplicationReadyEvent listener) so that JVM init doesn't
     * stall on slow networks. Failures per image are logged and tolerated:
     * the image will be re-pulled on first submission via [ensureImage].
     */
    fun prewarm() {
        for (image in knownImages) {
            // Skip pre-warm pull for images that are already local (e.g. custom-built kotlin tag
            // that doesn't exist in any registry).
            if (isImageLocal(image)) {
                logger.info { "Pre-warm skipped — image already cached locally: $image" }
                continue
            }
            logger.info { "Pre-warming image: $image" }
            try {
                val process = ProcessBuilder("docker", "pull", "--platform", "linux/amd64", image)
                    .redirectErrorStream(true)
                    .start()
                val finished = process.waitFor(180, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    logger.warn { "Pre-warm timed out for image: $image — will retry on first submission" }
                } else if (process.exitValue() != 0) {
                    val out = process.inputStream.bufferedReader().readText().trim()
                    logger.warn { "Pre-warm pull failed for $image (exit ${process.exitValue()}): $out — will retry on first submission" }
                } else {
                    logger.info { "Pre-warm done for image: $image" }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Pre-warm exception for image: $image — will retry on first submission" }
            }
        }
    }

    fun ensureImage(image: String): Boolean {
        if (isImageLocal(image)) {
            return true
        }
        logger.info { "Image $image not found locally, pulling..." }
        return pullImage(image)
    }

    private fun isImageLocal(image: String): Boolean {
        // We force linux/amd64 at runtime, so check that the amd64 variant
        // is the one stored locally. `docker image inspect` matches by tag,
        // not platform, so we additionally verify the manifest architecture.
        return try {
            val process = ProcessBuilder(
                "docker", "image", "inspect", "--format={{.Architecture}}", image
            ).redirectErrorStream(true).start()
            val finished = process.waitFor(10, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                false
            } else if (process.exitValue() != 0) {
                false
            } else {
                val arch = process.inputStream.bufferedReader().readText().trim()
                arch == "amd64"
            }
        } catch (e: Exception) {
            logger.warn(e) { "docker image inspect failed for $image" }
            false
        }
    }

    private fun pullImage(image: String): Boolean {
        return try {
            val process = ProcessBuilder("docker", "pull", "--platform", "linux/amd64", image)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(120, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                logger.error { "docker pull timed out for $image" }
                false
            } else {
                val ok = process.exitValue() == 0
                if (!ok) {
                    val out = process.inputStream.bufferedReader().readText().trim()
                    logger.error { "docker pull failed for $image: $out" }
                }
                ok
            }
        } catch (e: Exception) {
            logger.error(e) { "docker pull exception for $image" }
            false
        }
    }
}
