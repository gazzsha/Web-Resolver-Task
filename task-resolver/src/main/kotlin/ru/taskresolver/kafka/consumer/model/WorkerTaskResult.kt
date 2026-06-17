package ru.taskresolver.kafka.consumer.model

import ru.worker.model.TestStatus
import ru.worker.model.Verdict

data class WorkerTaskResult(
    val taskId: String,
    val testId: String,
    val code: String,
    val language: String,
    val status: String,
    val testResults: List<WorkerTestResult>,
    val totalExecutionTimeMs: Long,
    val memoryUsedKb: Long
)

data class WorkerTestResult(
    val testId: String,
    val status: TestStatus,
    val verdict: Verdict,
    val output: String?,
    val error: String?,
    val executionTimeMs: Long,
    val memoryUsedKb: Long
)
