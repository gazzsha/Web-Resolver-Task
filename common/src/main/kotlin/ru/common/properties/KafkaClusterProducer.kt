package ru.common.properties


open class KafkaClusterProducer(
    open val bootstrapServers: List<String>,
    open val topic: String
)
