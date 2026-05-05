package ru.sandbox

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import ru.sandbox.model.ExecutionStatus
import ru.sandbox.model.SandboxExecutionRequest
import ru.sandbox.model.SandboxExecutionResult
import ru.sandbox.service.DockerSandboxService
import ru.sandbox.service.SandboxImageManager
import java.util.UUID

/**
 * Integration tests for sandbox security hardening (Phase 7).
 *
 * Disabled by default: requires running Docker daemon and pulls
 * eclipse-temurin:21-jdk-alpine on first run.
 *
 * Run manually:
 *   ./gradlew :sandbox:test --tests "ru.sandbox.MaliciousCodeTests"
 */
@Disabled("Integration test — requires Docker daemon. Run manually for security verification.")
class MaliciousCodeTests {

    private val service = DockerSandboxService(SandboxImageManager())

    private fun runJava(
        code: String,
        timeoutSeconds: Long = 10,
        memoryLimitMb: Int = 128
    ): SandboxExecutionResult =
        service.execute(
            SandboxExecutionRequest(
                requestId = UUID.randomUUID(),
                code = code,
                language = "java",
                className = "Solution",
                testInput = "",
                expectedOutput = "",
                timeoutSeconds = timeoutSeconds,
                memoryLimitMb = memoryLimitMb,
                cpuLimit = 1.0
            )
        )

    @Test
    fun `infinite loop should hit TLE`() {
        val code = """
            public class Solution {
                public static void main(String[] args) {
                    while (true) { }
                }
            }
        """.trimIndent()

        val result = runJava(code, timeoutSeconds = 5)

        assertEquals(
            ExecutionStatus.TIME_LIMIT_EXCEEDED,
            result.status,
            "Infinite loop must be killed by sandbox timeout"
        )
    }

    @Test
    fun `huge array should hit MLE or RUNTIME_ERROR`() {
        val code = """
            public class Solution {
                public static void main(String[] args) {
                    int[] a = new int[Integer.MAX_VALUE];
                    System.out.println(a.length);
                }
            }
        """.trimIndent()

        val result = runJava(code, memoryLimitMb = 64)

        assertTrue(
            result.status in setOf(
                ExecutionStatus.MEMORY_LIMIT_EXCEEDED,
                ExecutionStatus.RUNTIME_ERROR
            ),
            "Huge allocation must be contained, expected MLE or RUNTIME_ERROR, got status=${result.status}"
        )
    }

    @Test
    fun `runtime exec rm should not succeed under hardened sandbox`() {
        val code = """
            public class Solution {
                public static void main(String[] args) throws Exception {
                    Process p = Runtime.getRuntime().exec(new String[]{"rm", "-rf", "/"});
                    p.waitFor();
                    System.out.println("danger");
                }
            }
        """.trimIndent()

        val result = runJava(code)

        assertNotEquals(
            ExecutionStatus.SUCCESS,
            result.status,
            "Malicious rm -rf must NOT report SUCCESS under hardened sandbox"
        )
    }
}
