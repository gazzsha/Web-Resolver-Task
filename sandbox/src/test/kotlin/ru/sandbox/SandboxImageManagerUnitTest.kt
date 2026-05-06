package ru.sandbox

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import ru.sandbox.service.SandboxImageManager

/**
 * Unit tests for SandboxImageManager that do not require a Docker daemon.
 *
 * ensureImage() calls `docker image inspect` as a subprocess. When Docker is
 * absent (typical CI without DinD) the ProcessBuilder will throw or return a
 * non-zero exit code — in both cases the method must return false rather than
 * throwing, guaranteeing graceful degradation.
 */
class SandboxImageManagerUnitTest {

    private val manager = SandboxImageManager()

    @Test
    fun `ensureImage returns false for non-existent image when docker unavailable`() {
        // "does-not-exist:latest" will never be locally cached.
        // If docker is available it will still return false (unknown image + pull failure).
        // If docker is absent the internal exception is caught and false is returned.
        val result = manager.ensureImage("does-not-exist:99999999-test-only")
        assertFalse(result, "ensureImage must return false rather than throw when image is unavailable")
    }

    @Test
    fun `ensureImage does not throw for completely unknown image name`() {
        // Purely compile + runtime smoke: no exception escapes the method.
        val result = runCatching { manager.ensureImage("totally/unknown:image-that-cannot-exist") }
        assert(result.isSuccess) { "ensureImage must not propagate exceptions: ${result.exceptionOrNull()}" }
    }
}
