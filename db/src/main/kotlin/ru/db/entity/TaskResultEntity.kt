package ru.db.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * Entity for storing task execution results
 */
@Entity
@Table(name = "task_results")
data class TaskResultEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "submission_id", nullable = false, unique = true)
    var submissionId: UUID,

    @Column(name = "task_id", nullable = false)
    var taskId: UUID,

    @Column(name = "test_id", nullable = false)
    var testId: UUID,

    @Column(name = "user_id")
    var userId: UUID? = null,

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    var status: TaskStatus,

    @Column(name = "total_tests")
    var totalTests: Int = 0,

    @Column(name = "passed_tests")
    var passedTests: Int = 0,

    @Column(name = "total_execution_time_ms")
    var totalExecutionTimeMs: Long = 0,

    @Column(name = "memory_used_kb")
    var memoryUsedKb: Long = 0,

    @Column(name = "code", columnDefinition = "TEXT")
    var code: String,

    @Column(name = "language", nullable = false)
    var language: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "test_results", columnDefinition = "jsonb")
    var testResults: List<TestResultJson> = emptyList(),

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scenario_results", columnDefinition = "jsonb")
    var scenarioResults: List<ScenarioResultJson> = emptyList(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_analysis_id")
    var aiAnalysis: AIAnalysisEntity? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)

enum class TaskStatus {
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILED,
    ERROR
}

/**
 * JSON representation of test result
 */
data class TestResultJson(
    val testId: UUID,
    val status: TestStatus,
    val verdict: Verdict,
    val output: String?,
    val error: String?,
    val executionTimeMs: Long,
    val memoryUsedKb: Long
)

enum class TestStatus {
    PASSED,
    FAILED,
    ERROR,
    SKIPPED
}

enum class Verdict {
    OK,
    WRONG_ANSWER,
    PRESENTATION_ERROR,
    TIME_LIMIT_EXCEEDED,
    MEMORY_LIMIT_EXCEEDED,
    RUNTIME_ERROR,
    COMPILATION_ERROR
}

/**
 * JSON representation of scenario result
 */
data class ScenarioResultJson(
    val scenarioId: UUID,
    val status: TestStatus,
    val stepResults: List<StepResultJson>,
    val finalState: String?
)

data class StepResultJson(
    val stepNumber: Int,
    val status: TestStatus,
    val actualOutput: String?,
    val expectedOutput: String?,
    val stateMatches: Boolean
)
