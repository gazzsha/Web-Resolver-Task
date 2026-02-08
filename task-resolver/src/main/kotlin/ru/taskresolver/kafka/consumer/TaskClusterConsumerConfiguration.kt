package ru.taskresolver.kafka.consumer

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import ru.common.utils.KafkaConfigurationUtils.consumerProperties

@EnableKafka
@Configuration
class TaskClusterConsumerConfiguration(
    private val KafkaClusterConsumer: TaskClusterKafkaPropertiesConsumer
) {

    @Bean
    fun taskClusterKafkaConsumerFactory(): DefaultKafkaConsumerFactory<String, String> {
        return DefaultKafkaConsumerFactory<String, String>(consumerProperties(KafkaClusterConsumer))
    }

    @Bean
    fun taskClusterKafkaListenerContainerFactory(
        @Qualifier("taskClusterKafkaConsumerFactory") consumerFactory: ConsumerFactory<String, String>
    ) = ConcurrentKafkaListenerContainerFactory<String, String>().apply { this.consumerFactory = consumerFactory }

}
