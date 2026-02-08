package ru.taskresolver.kafka.consumer

import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class TaskClusterKafkaListener(
    private val configuration: TaskClusterConsumerConfiguration
) {

    @KafkaListener(
        topics = [
            "\${kafka.clusters.kaas-common.topics.meeteor-meetings-raw.name}"
        ],
        groupId = "\${kafka.clusters.kaas-common.topics.meeteor-meetings-raw.group-id}",
        containerFactory = ,
        errorHandler = "defaultKafkaListenerErrorHandler",
        autoStartup = "\${kafka.clusters.kaas-common.topics.meeteor-meetings-raw.enabled:true}"
    )
    fun listen() {

    }
}
