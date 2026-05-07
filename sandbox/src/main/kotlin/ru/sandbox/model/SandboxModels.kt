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
 * Container-side execution metrics measured entirely on the host — no
 * program-internal markers, no stdout/stderr scraping.
 *
 * wallTimeMs      — duration from container StartedAt to FinishedAt (docker inspect)
 * peakMemoryBytes — running maximum sampled every 100 ms via docker stats
 * exitCode        — raw exit code returned by docker wait
 * verdict         — canonical verdict resolved from exit code, OOMKilled flag and stderr
 */
data class ExecutionMetrics(
    val wallTimeMs: Long,
    val peakMemoryBytes: Long,
    val exitCode: Int,
    val verdict: Verdict
)

/**
 * Result of code execution in sandbox
 */
data class SandboxExecutionResult(
    val requestId: UUID,
    val status: ExecutionStatus,
    val output: String?,
    val error: String?,
    /**
     * Wall-clock time in milliseconds measured on the host via docker inspect.
     * Replaces the former program-internal executionTimeMs marker.
     */
    val executionTimeMs: Long,
    /**
     * Peak container memory in kilobytes sampled via docker stats on the host.
     * Replaces the former program-internal memoryUsedKb marker.
     * 1 KiB = 1024 bytes.
     */
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
 * Canonical verdict enum.
 *
 * OK / WRONG_ANSWER / PRESENTATION_ERROR — output-comparison verdicts, set
 * by compareOutput() after a successful container exit.
 *
 * RUNTIME_ERROR / TIME_LIMIT_EXCEEDED / MEMORY_LIMIT_EXCEEDED /
 * COMPILATION_ERROR — execution-failure verdicts, set by resolveVerdict()
 * from host-observable signals (exit code, OOMKilled, stderr patterns).
 *
 * This enum is the single source of truth for verdict within the sandbox
 * module.  The worker module defines its own Verdict and maps from this one
 * in DockerTestEngine.
 */
enum class Verdict {
    OK,
    WRONG_ANSWER,
    PRESENTATION_ERROR,
    RUNTIME_ERROR,
    TIME_LIMIT_EXCEEDED,
    MEMORY_LIMIT_EXCEEDED,
    COMPILATION_ERROR
}
