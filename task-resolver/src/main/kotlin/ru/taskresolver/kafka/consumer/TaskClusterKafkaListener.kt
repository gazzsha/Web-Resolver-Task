package ru.taskresolver.kafka.consumer

import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class TaskClusterKafkaListener(
    private val configuration: TaskClusterConsumerConfiguration
) {

    @KafkaListener(
        topics = ["\${kafka.worker.topic:task-execution}"],
        groupId = "\${kafka.worker.group-id:task-resolver}",
        containerFactory = "kafkaListenerContainerFactory",
        autoStartup = "false"
    )
    fun listen() {
        // Placeholder for future implementation
    }
}
