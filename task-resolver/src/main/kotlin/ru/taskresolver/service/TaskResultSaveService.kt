package ru.taskresolver.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.db.entity.ScenarioResultJson
import ru.db.entity.StepResultJson
import ru.db.entity.TaskResultEntity
import ru.db.entity.TestResultJson
import ru.db.entity.SubmissionStatus
import ru.db.repository.SubmissionRepository
import ru.db.repository.TaskResultRepository
import ru.worker.model.TaskStatus
import ru.worker.model.TestStatus
import ru.worker.model.Verdict
import java.time.Instant
import java.util.*

@Service
class TaskResultSaveService(
    private val taskResultRepository: TaskResultRepository,
    private val submissionRepository: SubmissionRepository
) {

    @Transactional
    fun saveResult(
        submissionId: UUID,
        taskId: UUID,
        testId: UUID,
        code: String,
        language: String,
        testResults: List<TestExecutionResult>
    ): TaskResultEntity {
        val passedTests = testResults.count { it.status == TestStatus.PASSED }
        val totalTests = testResults.size
        val status = when {
            passedTests == totalTests -> TaskStatus.SUCCESS
            passedTests > 0 -> TaskStatus.PARTIAL_SUCCESS
            else -> TaskStatus.FAILED
        }

        val totalExecutionTime = testResults.sumOf { it.executionTimeMs }
        val maxMemoryUsed = testResults.maxOfOrNull { it.memoryUsedKb } ?: 0

        val entity = TaskResultEntity(
            submissionId = submissionId,
            taskId = taskId,
            testId = testId,
            status = status,
            totalTests = totalTests,
            passedTests = passedTests,
            totalExecutionTimeMs = totalExecutionTime,
            memoryUsedKb = maxMemoryUsed,
            code = code,
            language = language,
            testResults = testResults.map { it.toJson() },
            createdAt = Instant.now()
        )

        submissionRepository.findById(submissionId).ifPresent { submission ->
            submission.status = SubmissionStatus.COMPLETED
            submission.updatedAt = Instant.now()
            submissionRepository.save(submission)
        }

        return taskResultRepository.save(entity)
    }

    @Transactional(readOnly = true)
    fun getResultBySubmissionId(submissionId: UUID): TaskResultEntity? {
        return taskResultRepository.findBySubmissionId(submissionId)
    }

    @Transactional(readOnly = true)
    fun getResultById(id: Long): TaskResultEntity? {
        return taskResultRepository.findById(id).orElse(null)
    }

    fun getResultsByTestId(testId: UUID): List<TaskResultEntity> {
        return taskResultRepository.findByTestId(testId)
    }
}

data class TestExecutionResult(
    val testId: UUID,
    val status: TestStatus,
    val verdict: Verdict,
    val output: String?,
    val error: String?,
    val executionTimeMs: Long,
    val memoryUsedKb: Long
) {
    fun toJson(): TestResultJson {
        return TestResultJson(
            testId = testId,
            status = status,
            verdict = verdict,
            output = output,
            error = error,
            executionTimeMs = executionTimeMs,
            memoryUsedKb = memoryUsedKb
        )
    }
}
