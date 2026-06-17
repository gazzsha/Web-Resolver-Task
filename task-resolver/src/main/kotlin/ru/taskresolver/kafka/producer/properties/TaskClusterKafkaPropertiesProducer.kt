package ru.taskresolver.kafka.producer.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import ru.common.properties.KafkaClusterProducer

@ConfigurationProperties(prefix = "kafka.config.task-cluster.producer")
data class TaskClusterKafkaPropertiesProducer(
    override val bootstrapServers: List<String>,
    override val topic: String
) : KafkaClusterProducer(bootstrapServers, topic)
