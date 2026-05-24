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
 * Kafka listener for worker tasks.
 *
 * Error handling: no try/catch here. The container's [DefaultErrorHandler]
 * (configured in KafkaWorkerConfig) retries on transient failure and routes
 * exhausted retries to task-execution.DLT. Catching here would re-introduce
 * the silent-ack bug (F-16) — every failure would be swallowed and the
 * submission lost without surfacing.
 */
@Component
class WorkerKafkaListener(
    private val workerService: WorkerService,
    private val resultPublisher: WorkerResultPublisher,
) {

    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    @KafkaListener(
        topics = ["\${kafka.worker.topic:task-execution}"],
        groupId = "\${kafka.worker.group-id:worker-group}",
        containerFactory = "workerKafkaListenerContainerFactory"
    )
    fun listenTask(record: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        logger.info { "Received task message from Kafka: topic=${record.topic()}, size=${record.value()?.length ?: 0}" }

        // F-17: cap payload before deserialisation. Bounds JVM heap under load
        // (one submission × 3 concurrency × ~20× AST allocation factor).
        require((record.value()?.length ?: 0) <= MAX_CODE_LENGTH * 2) {
            "Kafka payload too large (${record.value()?.length ?: 0} chars > ${MAX_CODE_LENGTH * 2}). Rejecting."
        }

        val taskMessage = objectMapper.readValue(record.value(), WorkerTaskMessage::class.java)
        require(taskMessage.code.length <= MAX_CODE_LENGTH) {
            "Submission code too large (${taskMessage.code.length} chars > $MAX_CODE_LENGTH). Rejecting."
        }
        logger.info { "Processing task ${taskMessage.taskId}, codeLen=${taskMessage.code.length}" }

        val result = workerService.processTask(taskMessage)
        resultPublisher.publishResult(result)
        acknowledgment.acknowledge()

        logger.info { "Task ${taskMessage.taskId} completed" }
    }

    companion object {
        // F-17: hard cap on submitted code. 64 KiB ≈ 1500 lines of student
        // code — well above any sane competitive-programming solution.
        const val MAX_CODE_LENGTH = 65_536
    }
}
