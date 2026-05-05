package ru.worker.service

import io.github.oshai.kotlinlogging.KotlinLogging
import ru.sandbox.model.SandboxExecutionRequest
import ru.sandbox.model.Verdict
import ru.sandbox.service.DockerSandboxService
import ru.worker.model.TestCase
import ru.worker.model.TestResult
import ru.worker.model.TestStatus
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Test Engine implementation using Docker Sandbox for secure code execution
 */
class DockerTestEngine(
    private val sandboxService: DockerSandboxService
) : TestEngine {

    override fun runTest(code: String, language: String, testCase: TestCase): TestResult {
        logger.info { "Running test ${testCase.testId} in Docker sandbox" }
        logger.info { "Test input: ${testCase.input}" }
        logger.info { "Expected output: ${testCase.expectedOutput}" }

        try {
            // Create sandbox execution request
            val request = SandboxExecutionRequest(
                requestId = UUID.randomUUID(),
                code = code,
                language = language,
                testInput = testCase.input,
                expectedOutput = testCase.expectedOutput,
                className = extractClassName(code) ?: "Solution",
                timeoutSeconds = 10L,
                memoryLimitMb = 256,
                cpuLimit = 1.0
            )

            logger.info { "Executing sandbox request: ${request.requestId}" }

            // Execute in Docker sandbox
            val executionResult = sandboxService.execute(request)

            logger.info { "Sandbox execution completed: status=${executionResult.status}, output=${executionResult.output}" }

            // Map sandbox result to test result
            val status = when (executionResult.status) {
                ru.sandbox.model.ExecutionStatus.SUCCESS -> {
                    if (executionResult.verdict == Verdict.OK) TestStatus.PASSED else TestStatus.FAILED
                }
                ru.sandbox.model.ExecutionStatus.COMPILATION_ERROR -> TestStatus.ERROR
                ru.sandbox.model.ExecutionStatus.RUNTIME_ERROR -> TestStatus.ERROR
                ru.sandbox.model.ExecutionStatus.TIME_LIMIT_EXCEEDED -> TestStatus.ERROR
                ru.sandbox.model.ExecutionStatus.MEMORY_LIMIT_EXCEEDED -> TestStatus.ERROR
                ru.sandbox.model.ExecutionStatus.SECURITY_VIOLATION -> TestStatus.ERROR
                ru.sandbox.model.ExecutionStatus.INTERNAL_ERROR -> TestStatus.ERROR
            }

            val verdict = when (executionResult.verdict) {
                Verdict.OK -> ru.worker.model.Verdict.OK
                Verdict.WRONG_ANSWER -> ru.worker.model.Verdict.WRONG_ANSWER
                Verdict.PRESENTATION_ERROR -> ru.worker.model.Verdict.PRESENTATION_ERROR
                null -> when (executionResult.status) {
                    ru.sandbox.model.ExecutionStatus.COMPILATION_ERROR -> ru.worker.model.Verdict.COMPILATION_ERROR
                    ru.sandbox.model.ExecutionStatus.RUNTIME_ERROR -> ru.worker.model.Verdict.RUNTIME_ERROR
                    ru.sandbox.model.ExecutionStatus.TIME_LIMIT_EXCEEDED -> ru.worker.model.Verdict.TIME_LIMIT_EXCEEDED
                    ru.sandbox.model.ExecutionStatus.MEMORY_LIMIT_EXCEEDED -> ru.worker.model.Verdict.MEMORY_LIMIT_EXCEEDED
                    ru.sandbox.model.ExecutionStatus.SECURITY_VIOLATION -> ru.worker.model.Verdict.RUNTIME_ERROR
                    else -> ru.worker.model.Verdict.RUNTIME_ERROR
                }
            }

            return TestResult(
                testId = testCase.testId,
                status = status,
                verdict = verdict,
                output = executionResult.output,
                error = executionResult.error,
                executionTimeMs = executionResult.executionTimeMs,
                memoryUsedKb = executionResult.memoryUsedKb
            )
        } catch (e: Exception) {
            logger.error(e) { "Test execution failed for ${testCase.testId}" }
            return TestResult(
                testId = testCase.testId,
                status = TestStatus.ERROR,
                verdict = ru.worker.model.Verdict.RUNTIME_ERROR,
                output = null,
                error = e.message,
                executionTimeMs = 0,
                memoryUsedKb = 0
            )
        }
    }

    override fun runTests(code: String, language: String, testCases: List<TestCase>): List<TestResult> {
        return testCases.map { testCase -> runTest(code, language, testCase) }
    }

    private fun extractClassName(code: String): String? {
        val classPattern = Regex("""(?:public\s+)?class\s+(\w+)""")
        return classPattern.find(code)?.groupValues?.get(1)
    }
}
