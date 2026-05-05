package ru.worker.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import ru.worker.model.WorkerTaskResult

private val logger = KotlinLogging.logger {}

/**
 * Publisher that sends results via Kafka to main application
 */
@Component
class WorkerResultPublisher(
    @Qualifier("workerKafkaTemplate") private val kafkaTemplate: KafkaTemplate<String, String>
) {
    private val objectMapper = ObjectMapper()



    fun publishResult(result: WorkerTaskResult) {
        try {
            val messageJson = objectMapper.writeValueAsString(result)
            
            logger.info { "Sending result to Kafka topic 'task-results' for task ${result.taskId}" }
            
            val future = kafkaTemplate.send("task-results", result.taskId.toString(), messageJson)
            future.whenComplete { _, exception ->
                if (exception != null) {
                    logger.error(exception) { "Failed to send result to Kafka for task ${result.taskId}" }
                } else {
                    logger.info { "Successfully sent result to Kafka for task ${result.taskId}" }
                }
            }
            
            // Wait for send to complete
            future.get()
        } catch (e: Exception) {
            logger.error(e) { "Failed to publish result for task ${result.taskId}" }
        }
    }
}
