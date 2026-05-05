package ru.sandbox.model

import java.util.UUID

/**
 * Request for code execution in sandbox
 */
data class SandboxExecutionRequest(
    val requestId: UUID,
    val code: String,
    val language: String,
    val className: String,
    val testInput: String,
    val expectedOutput: String,
    val timeoutSeconds: Long = 5,
    val memoryLimitMb: Int = 256,
    val cpuLimit: Double = 1.0
)

/**
 * Result of code execution in sandbox
 */
data class SandboxExecutionResult(
    val requestId: UUID,
    val status: ExecutionStatus,
    val output: String?,
    val error: String?,
    val executionTimeMs: Long,
    val memoryUsedKb: Long,
    val verdict: Verdict? = null
)

/**
 * Status of sandbox execution
 */
enum class ExecutionStatus {
    SUCCESS,
    RUNTIME_ERROR,
    TIME_LIMIT_EXCEEDED,
    MEMORY_LIMIT_EXCEEDED,
    COMPILATION_ERROR,
    SECURITY_VIOLATION,
    INTERNAL_ERROR
}

/**
 * Verdict for test comparison
 */
enum class Verdict {
    OK,
    WRONG_ANSWER,
    PRESENTATION_ERROR
}
