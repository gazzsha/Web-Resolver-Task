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



    /**
     * Publishes the worker result to `task-results`.
     *
     * F-18: rethrows on failure. The caller (WorkerKafkaListener) does NOT
     * catch — the broker outage propagates up to the container's
     * DefaultErrorHandler (F-16) which retries 3× before DLT, and crucially
     * does NOT acknowledge the input record. Without this rethrow, a Kafka
     * outage during result publish would silently advance the input offset
     * and lose the student's submission.
     */
    fun publishResult(result: WorkerTaskResult) {
        val messageJson = objectMapper.writeValueAsString(result)

        logger.info { "Sending result to Kafka topic 'task-results' for task ${result.taskId}" }

        val future = kafkaTemplate.send("task-results", result.taskId.toString(), messageJson)
        // Block until ack/error. Failure throws ExecutionException up to caller.
        future.get()
        logger.info { "Successfully sent result to Kafka for task ${result.taskId}" }
    }
}
