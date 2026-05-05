package ru.worker.service

import ru.worker.model.*

/**
 * Test Engine for running and validating test cases
 */
interface TestEngine {
    fun runTest(code: String, language: String, testCase: TestCase): TestResult
    fun runTests(code: String, language: String, testCases: List<TestCase>): List<TestResult>
}
