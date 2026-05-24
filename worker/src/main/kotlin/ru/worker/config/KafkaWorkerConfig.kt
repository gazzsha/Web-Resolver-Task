package ru.worker.config

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.*
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff

/**
 * Kafka configuration for Worker
 */
@Configuration
class KafkaWorkerConfig {

    @Value("\${spring.kafka.bootstrap-servers:localhost:9092}")
    private val bootstrapServers: String = "localhost:9092"

    @Value("\${kafka.worker.group-id:worker-group}")
    private val groupId: String = "worker-group"

    @Bean
    fun workerConsumerFactory(): ConsumerFactory<String, String> {
        val props = mapOf<String, Any>(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to groupId,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            // F-17: cap per-partition fetch at 1 MiB. Default 1 MiB matches Kafka
            // broker message.max.bytes default; explicit set keeps the cap stable
            // even if the operator bumps broker config to fit larger payloads.
            ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG to 1_048_576,
            // F-17: one record per poll — when combined with worker.concurrency=3
            // in WorkerServiceConfig, the worker processes at most 3 concurrent
            // submissions, bounding heap pressure under load.
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 1
        )
        return DefaultKafkaConsumerFactory(props)
    }

    @Bean
    fun workerKafkaListenerContainerFactory(
        @Qualifier("workerConsumerFactory") consumerFactory: ConsumerFactory<String, String>,
        @Qualifier("workerKafkaTemplate") workerKafkaTemplate: KafkaTemplate<String, String>,
    ): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = consumerFactory
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
        // F-16: route poison messages to task-execution.DLT after 3 fast retries.
        // Default behaviour without this handler is "log+ack" inside the listener,
        // which silently loses every transient failure (Docker hiccup, broker
        // outage, malformed JSON). With the recoverer, the DLT topic carries the
        // raw record + failure cause for manual replay.
        val recoverer = DeadLetterPublishingRecoverer(workerKafkaTemplate)
        val errorHandler = DefaultErrorHandler(recoverer, FixedBackOff(500L, 3L))
        factory.setCommonErrorHandler(errorHandler)
        return factory
    }

    @Bean
    fun workerProducerFactory(): ProducerFactory<String, String> {
        val props = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.RETRIES_CONFIG to 3,
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true
        )
        return DefaultKafkaProducerFactory(props)
    }

    @Bean
    fun workerKafkaTemplate(
        @Qualifier("workerProducerFactory") producerFactory: ProducerFactory<String, String>
    ): KafkaTemplate<String, String> {
        return KafkaTemplate(producerFactory)
    }
}
