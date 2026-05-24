package ru.worker.model

import java.util.UUID

/**
 * Worker task message from Kafka queue
 */
data class WorkerTaskMessage(
    val taskId: UUID,
    val testId: UUID,
    val code: String,
    val language: String,
    val className: String = "Solution",
    val testCases: List<TestCase>,
    val scenarioTests: List<ScenarioTest>? = null,
    // P0-3: условие задачи прокидывается из task-resolver через Kafka.
    // null если сообщение опубликовано legacy-кодом без этого поля.
    val taskDescription: String? = null
)

/**
 * Single test case for execution
 */
data class TestCase(
    val input: String,
    val expectedOutput: String,
    val testId: UUID
)

/**
 * Scenario test reference
 */
data class ScenarioTest(
    val scenarioId: UUID,
    val steps: List<ScenarioStep>
)

data class ScenarioStep(
    val stepNumber: Int,
    val input: String,
    val expectedOutput: String? = null
)

/**
 * Result of worker task execution
 */
data class WorkerTaskResult(
    val taskId: UUID,
    val testId: UUID,
    val code: String,
    val language: String,
    val status: TaskStatus,
    val testResults: List<TestResult>,
    val scenarioResults: List<ScenarioResult>,
    val aiAnalysis: ru.aianalyzer.service.AIAnalysisResult?,
    val totalExecutionTimeMs: Long,
    val memoryUsedKb: Long
)

/**
 * Result of single test execution
 */
data class TestResult(
    val testId: UUID,
    val status: TestStatus,
    val verdict: Verdict,
    val output: String?,
    val error: String?,
    val executionTimeMs: Long,
    val memoryUsedKb: Long
)

/**
 * Result of scenario test execution
 */
data class ScenarioResult(
    val scenarioId: UUID,
    val status: TestStatus,
    val stepResults: List<StepResult>,
    val finalState: String?
)

data class StepResult(
    val stepNumber: Int,
    val status: TestStatus,
    val actualOutput: String?,
    val expectedOutput: String?,
    val stateMatches: Boolean
)

/**
 * Overall task status
 */
enum class TaskStatus {
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILED,
    ERROR
}

/**
 * Test execution status
 */
enum class TestStatus {
    PASSED,
    FAILED,
    ERROR,
    SKIPPED
}

/**
 * Test verdict
 */
enum class Verdict {
    OK,
    WRONG_ANSWER,
    PRESENTATION_ERROR,
    TIME_LIMIT_EXCEEDED,
    MEMORY_LIMIT_EXCEEDED,
    RUNTIME_ERROR,
    COMPILATION_ERROR
}
