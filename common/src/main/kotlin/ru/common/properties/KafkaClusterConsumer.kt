package ru.common.properties

open class KafkaClusterConsumer(
    open val bootstrapServers: List<String>,
    open val topic: String,
    open val groupId: String
)

