package ru.worker.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import ru.worker.model.WorkerTaskMessage
import ru.worker.service.WorkerService

private val logger = KotlinLogging.logger {}

/**
 * Kafka listener for worker tasks
 */
@Component
class WorkerKafkaListener(
    private val workerService: WorkerService,
    private val resultPublisher: WorkerResultPublisher
) {

    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    @KafkaListener(
        topics = ["\${kafka.worker.topic:task-execution}"],
        groupId = "\${kafka.worker.group-id:worker-group}",
        containerFactory = "workerKafkaListenerContainerFactory"
    )
    fun listenTask(record: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        logger.info { "Received task message from Kafka: topic=${record.topic()}" }

        try {
            val taskMessage = objectMapper.readValue(record.value(), WorkerTaskMessage::class.java)
            logger.info { "Processing task ${taskMessage.taskId}" }

            val result = workerService.processTask(taskMessage)
            resultPublisher.publishResult(result)
            acknowledgment.acknowledge()

            logger.info { "Task ${taskMessage.taskId} completed" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to process task message" }
            acknowledgment.acknowledge()
        }
    }
}
