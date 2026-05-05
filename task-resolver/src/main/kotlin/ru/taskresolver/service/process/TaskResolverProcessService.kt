package ru.taskresolver.service.process

import com.fasterxml.jackson.databind.ObjectMapper
import model.StartTaskRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import ru.db.entity.SubmissionEntity
import ru.db.entity.SubmissionStatus
import ru.db.repository.SubmissionRepository
import ru.taskresolver.kafka.producer.TaskClusterProducer
import ru.taskresolver.kafka.producer.model.TestCase
import ru.taskresolver.kafka.producer.model.TaskMessage
import ru.taskresolver.kafka.producer.model.parseTestCasesFromJson
import ru.taskresolver.service.TestService
import java.time.Instant
import java.util.*

@Service
class TaskResolverProcessService(
    private val testService: TestService,
    private val taskClusterProducer: TaskClusterProducer,
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val submissionRepository: SubmissionRepository
) {

    fun processStartTaskToResolve(request: StartTaskRequest): UUID {
        val test = testService.getTestById(request.testId)
        val testCases = getTestCases(request.testId)

        // Generate submission ID
        val submissionId = UUID.randomUUID()

        // Save submission to database
        val submission = SubmissionEntity(
            id = submissionId,
            taskId = test.testId,  // Use testId as taskId
            testId = test.testId,
            code = request.code,
            language = request.language.value,
            status = SubmissionStatus.PENDING,
            createdAt = Instant.now()
        )
        submissionRepository.save(submission)

        val taskMessage = TaskMessage(
            taskId = submissionId,
            code = request.code,
            language = request.language.value,
            testId = test.testId,
            testCases = testCases
        )

        taskClusterProducer.publish(taskMessage)
        return submissionId
    }

    private fun getTestCases(testId: UUID): List<TestCase> {
        val sql = """
            SELECT tests FROM test_resolve
            WHERE problem_id = ?
        """.trimIndent()

        return try {
            val testsJson = jdbcTemplate.queryForObject(sql, String::class.java, testId)
            if (testsJson != null) {
                parseTestCasesFromJson(testsJson, objectMapper)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
