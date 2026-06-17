package ru.worker.config

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.sandbox.service.DockerSandboxService
import ru.scenarioplayer.DefaultScenarioRunnerImpl

/**
 * F-21 regression: the @Bean factory in WorkerServiceConfig MUST return the
 * real DefaultScenarioRunnerImpl, not an anonymous "always PASSED" stub.
 *
 * Before the fix, the stub silently passed every scenario step regardless of
 * code/language/input, letting a student submitting `print("OK")` earn full
 * marks for any interactive scenario task. This test pins the bean's runtime
 * type so the regression cannot return unnoticed.
 */
class WorkerServiceConfigTest {

    @Test
    fun `scenarioRunner bean is the real DefaultScenarioRunnerImpl`() {
        val config = WorkerServiceConfig()
        val sandbox = mockk<DockerSandboxService>(relaxed = true)

        val runner = config.scenarioRunner(sandbox)

        // Pin the exact class — anything else (e.g. an anonymous object) fails.
        assertEquals(
            DefaultScenarioRunnerImpl::class.java,
            runner.javaClass,
            "scenarioRunner() must return DefaultScenarioRunnerImpl. " +
                "If you see another type here, the regression of F-21 is back: " +
                "an 'always PASSED' stub will silently bypass scenario grading.",
        )
    }
}
