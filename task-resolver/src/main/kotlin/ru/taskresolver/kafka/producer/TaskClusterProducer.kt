package ru.taskresolver.kafka.producer

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import ru.common.utils.sendSyncAndLog
import ru.taskresolver.kafka.producer.model.TaskMessage
import ru.taskresolver.kafka.producer.properties.TaskClusterKafkaPropertiesProducer

@Component
class TaskClusterProducer(
    private val objectMapper: ObjectMapper,
    private val taskClusterKafkaPropertiesProducer: TaskClusterKafkaPropertiesProducer,
    @Qualifier("taskClusterKafkaTemplate") private val kafkaTemplate: KafkaTemplate<String, String>
) {
    fun publish(event: TaskMessage) {
        val data = objectMapper.writeValueAsString(event)
        kafkaTemplate.sendSyncAndLog(taskClusterKafkaPropertiesProducer.topic, data)
    }
}
