package ru.taskresolver.kafka.consumer

import org.springframework.boot.context.properties.ConfigurationProperties
import ru.common.properties.KafkaClusterConsumer

@ConfigurationProperties(prefix = "kafka.config.task-cluster.consumer")
data class TaskClusterKafkaPropertiesConsumer(
    override val bootstrapServers: List<String>,
    override val topic: String,
    override val groupId: String
) : KafkaClusterConsumer(bootstrapServers, topic, groupId)
