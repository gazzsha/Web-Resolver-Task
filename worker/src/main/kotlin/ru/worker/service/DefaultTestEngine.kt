package ru.worker.service

import ru.sandbox.model.SandboxExecutionRequest
import ru.sandbox.model.SandboxExecutionResult
import ru.sandbox.service.DockerSandboxService
import ru.worker.model.*
import java.util.*

/**
 * Default test engine with Docker Sandbox for real code execution
 */
class DefaultTestEngine(
    private val sandboxService: DockerSandboxService
) : TestEngine {

    override fun runTest(code: String, language: String, testCase: TestCase): TestResult {
        try {
            val sandboxRequest = SandboxExecutionRequest(
                requestId = UUID.randomUUID(),
                code = code,
                language = language,
                className = "Solution",
                testInput = testCase.input,
                expectedOutput = testCase.expectedOutput,
                timeoutSeconds = 5,
                memoryLimitMb = 256,
                cpuLimit = 1.0
            )

            val sandboxResult = sandboxService.execute(sandboxRequest)

            val (status, verdict) = determineStatus(sandboxResult)

            return TestResult(
                testId = testCase.testId,
                status = status,
                verdict = verdict,
                output = sandboxResult.output,
                error = sandboxResult.error,
                executionTimeMs = sandboxResult.executionTimeMs,
                memoryUsedKb = sandboxResult.memoryUsedKb
            )
        } catch (e: Exception) {
            return TestResult(
                testId = testCase.testId ?: UUID.randomUUID(),
                status = TestStatus.ERROR,
                verdict = Verdict.RUNTIME_ERROR,
                output = null,
                error = e.message,
                executionTimeMs = 0,
                memoryUsedKb = 0
            )
        }
    }

    override fun runTests(code: String, language: String, testCases: List<TestCase>): List<TestResult> {
        return testCases.map { runTest(code, language, it) }
    }

    private fun determineStatus(result: SandboxExecutionResult): Pair<TestStatus, Verdict> {
        val status = when (result.status) {
            ru.sandbox.model.ExecutionStatus.SUCCESS -> TestStatus.PASSED
            ru.sandbox.model.ExecutionStatus.COMPILATION_ERROR -> TestStatus.FAILED
            ru.sandbox.model.ExecutionStatus.TIME_LIMIT_EXCEEDED -> TestStatus.FAILED
            ru.sandbox.model.ExecutionStatus.MEMORY_LIMIT_EXCEEDED -> TestStatus.FAILED
            ru.sandbox.model.ExecutionStatus.RUNTIME_ERROR -> TestStatus.FAILED
            ru.sandbox.model.ExecutionStatus.SECURITY_VIOLATION -> TestStatus.FAILED
            ru.sandbox.model.ExecutionStatus.INTERNAL_ERROR -> TestStatus.ERROR
        }

        val verdict = when (result.status) {
            ru.sandbox.model.ExecutionStatus.SUCCESS -> {
                result.verdict?.toVerdict() ?: Verdict.OK
            }
            ru.sandbox.model.ExecutionStatus.COMPILATION_ERROR -> Verdict.COMPILATION_ERROR
            ru.sandbox.model.ExecutionStatus.TIME_LIMIT_EXCEEDED -> Verdict.TIME_LIMIT_EXCEEDED
            ru.sandbox.model.ExecutionStatus.MEMORY_LIMIT_EXCEEDED -> Verdict.MEMORY_LIMIT_EXCEEDED
            ru.sandbox.model.ExecutionStatus.RUNTIME_ERROR -> Verdict.RUNTIME_ERROR
            ru.sandbox.model.ExecutionStatus.SECURITY_VIOLATION -> Verdict.RUNTIME_ERROR
            ru.sandbox.model.ExecutionStatus.INTERNAL_ERROR -> Verdict.RUNTIME_ERROR
            else -> Verdict.RUNTIME_ERROR
        }

        return status to verdict
    }
}

private fun ru.sandbox.model.Verdict.toVerdict(): Verdict {
    return when (this) {
        ru.sandbox.model.Verdict.OK -> Verdict.OK
        ru.sandbox.model.Verdict.WRONG_ANSWER -> Verdict.WRONG_ANSWER
        ru.sandbox.model.Verdict.PRESENTATION_ERROR -> Verdict.PRESENTATION_ERROR
    }
}
