package ru.worker.service

import ru.worker.model.*
import java.util.*

/**
 * Simple test engine for demo - executes tests without Docker
 * Returns mock successful results for demonstration
 */
class SimpleTestEngine : TestEngine {

    override fun runTest(code: String, language: String, testCase: TestCase): TestResult {
        // For demo purposes - simulate successful execution
        // In production, this would use DockerSandboxService
        return TestResult(
            testId = testCase.testId,
            status = TestStatus.PASSED,
            verdict = Verdict.OK,
            output = "[0, 1]",
            error = null,
            executionTimeMs = (20..100).random().toLong(),
            memoryUsedKb = (512..1024).random().toLong()
        )
    }

    override fun runTests(code: String, language: String, testCases: List<TestCase>): List<TestResult> {
        return testCases.map { runTest(code, language, it) }
    }
}
