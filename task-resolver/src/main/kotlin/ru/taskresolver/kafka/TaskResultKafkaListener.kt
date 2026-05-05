package ru.taskresolver.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import ru.taskresolver.service.TaskResultSaveService
import java.util.*

private val logger = KotlinLogging.logger {}

/**
 * Kafka listener for receiving task results from Worker
 */
@Component
class TaskResultKafkaListener(
    private val taskResultSaveService: TaskResultSaveService,
    private val objectMapper: ObjectMapper
) {

    @KafkaListener(
        topics = ["task-results"],
        groupId = "result-saver-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun listenResult(record: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        logger.info { "Received result from Kafka: topic=${record.topic()}, key=${record.key()}" }

        try {
            // Parse WorkerTaskResult from Kafka message
            val jsonNode = objectMapper.readTree(record.value())

            // submissionId is the same as taskId in the message
            val submissionId = UUID.fromString(jsonNode.get("taskId").asText())
            val taskId = submissionId // For backward compatibility
            val testId = UUID.fromString(jsonNode.get("testId").asText())
            val code = jsonNode.get("code").asText()
            val language = jsonNode.get("language").asText()
            val statusStr = jsonNode.get("status").asText()

            val testResultsNode = jsonNode.get("testResults")
            val testResults = testResultsNode.map { tr ->
                ru.taskresolver.service.TestExecutionResult(
                    testId = UUID.fromString(tr.get("testId").asText()),
                    status = ru.db.entity.TestStatus.valueOf(tr.get("status").asText()),
                    verdict = ru.db.entity.Verdict.valueOf(tr.get("verdict").asText()),
                    output = tr.get("output")?.asText(null),
                    error = tr.get("error")?.asText(null),
                    executionTimeMs = tr.get("executionTimeMs").asLong(),
                    memoryUsedKb = tr.get("memoryUsedKb").asLong()
                )
            }

            // Save to database using submissionId
            val savedResult = taskResultSaveService.saveResult(
                submissionId = submissionId,
                taskId = taskId,
                testId = testId,
                code = code,
                language = language,
                testResults = testResults
            )

            logger.info { "Saved result for submission $submissionId with status $statusStr" }
            acknowledgment.acknowledge()
        } catch (e: Exception) {
            logger.error(e) { "Failed to process result message" }
            acknowledgment.acknowledge()
        }
    }
}
