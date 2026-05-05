package ru.taskresolver.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import ru.taskresolver.service.TaskResultSaveService
import ru.taskresolver.service.TestExecutionResult
import ru.worker.model.TestStatus
import ru.worker.model.Verdict
import java.util.*

private val logger = KotlinLogging.logger {}

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
            val jsonNode = objectMapper.readTree(record.value())

            val submissionId = UUID.fromString(jsonNode.get("taskId").asText())
            val taskId = submissionId
            val testId = UUID.fromString(jsonNode.get("testId").asText())
            val code = jsonNode.get("code").asText()
            val language = jsonNode.get("language").asText()
            val statusStr = jsonNode.get("status").asText()

            val testResultsNode = jsonNode.get("testResults")
            val testResults = testResultsNode.map { tr ->
                TestExecutionResult(
                    testId = UUID.fromString(tr.get("testId").asText()),
                    status = TestStatus.valueOf(tr.get("status").asText()),
                    verdict = Verdict.valueOf(tr.get("verdict").asText()),
                    output = tr.get("output")?.asText(null),
                    error = tr.get("error")?.asText(null),
                    executionTimeMs = tr.get("executionTimeMs").asLong(),
                    memoryUsedKb = tr.get("memoryUsedKb").asLong()
                )
            }

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
