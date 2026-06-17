package ru.common.utils

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import ru.common.properties.KafkaClusterConsumer
import ru.common.properties.KafkaClusterProducer

object KafkaConfigurationUtils {
    private const val ENABLE_AUTOCOMMIT = true
    private const val AUTO_OFFSET_RESET = "earliest"
    private const val MAX_POLL_INTERVAL_MS = 30000

    fun producerProperties(kafkaClusterProducer: KafkaClusterProducer,
                           valueSerializerClass: Class<out Serializer<out Any>> = StringSerializer::class.java) = mapOf(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaClusterProducer.bootstrapServers,
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to valueSerializerClass
    )

    fun consumerProperties(kafkaCluster: KafkaClusterConsumer,
                           valueDeserializerClass: Class<out Deserializer<out Any>> = StringDeserializer::class.java) = mapOf(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaCluster.bootstrapServers,
        ConsumerConfig.GROUP_ID_CONFIG to kafkaCluster.groupId,
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to valueDeserializerClass,
        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to ENABLE_AUTOCOMMIT,
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to AUTO_OFFSET_RESET,
        ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG to MAX_POLL_INTERVAL_MS
    )
}




