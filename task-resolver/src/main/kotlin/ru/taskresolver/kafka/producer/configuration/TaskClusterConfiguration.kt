package ru.taskresolver.kafka.producer.configuration

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import ru.common.utils.KafkaConfigurationUtils.producerProperties
import ru.taskresolver.kafka.producer.properties.TaskClusterKafkaPropertiesProducer

@EnableKafka
@Configuration
class TaskClusterConfiguration(
    private val kafkaClusterConfig: TaskClusterKafkaPropertiesProducer,
) {
    @Bean
    fun taskClusterKafkaProducerFactory(): DefaultKafkaProducerFactory<String, String> =
        DefaultKafkaProducerFactory<String, String>(producerProperties(kafkaClusterConfig))

    @Bean
    fun taskClusterKafkaTemplate(
        @Qualifier("taskClusterKafkaProducerFactory") producerFactory: ProducerFactory<String, String>
    ): KafkaTemplate<String, String> = KafkaTemplate(producerFactory)
}
