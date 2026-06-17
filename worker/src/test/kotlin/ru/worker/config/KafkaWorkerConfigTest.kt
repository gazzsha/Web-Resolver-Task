package ru.worker.config

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.DefaultErrorHandler

/**
 * F-16 regression: the workerKafkaListenerContainerFactory MUST be wired with
 * a [DefaultErrorHandler] (which routes exhausted retries to a Dead Letter
 * Topic via [DeadLetterPublishingRecoverer]).
 *
 * If this assertion fails, the regression of F-16 is back: the listener will
 * fall through to the legacy "log + swallow + ack" behaviour and silently
 * lose every transient failure.
 */
class KafkaWorkerConfigTest {

    @Test
    fun `workerKafkaListenerContainerFactory wires DefaultErrorHandler`() {
        val config = KafkaWorkerConfig()
        val consumerFactory = mockk<ConsumerFactory<String, String>>(relaxed = true)
        val kafkaTemplate = mockk<KafkaTemplate<String, String>>(relaxed = true)

        val factory = config.workerKafkaListenerContainerFactory(consumerFactory, kafkaTemplate)

        assertNotNull(factory.containerProperties.ackMode) { "ackMode must remain MANUAL" }

        val handler = factory.containerProperties.let { _ ->
            // commonErrorHandler is set via factory.setCommonErrorHandler; expose via reflection
            // since the public getter on ConcurrentKafkaListenerContainerFactory is package-private.
            val field = factory.javaClass.superclass.getDeclaredField("commonErrorHandler")
            field.isAccessible = true
            field.get(factory)
        }
        assertNotNull(handler) {
            "F-16 regression: commonErrorHandler is null. Listener will fall back to legacy " +
                "log-and-ack behaviour and silently lose every transient failure."
        }
        assertEquals(
            DefaultErrorHandler::class.java,
            handler.javaClass,
            "commonErrorHandler must be DefaultErrorHandler (which uses DeadLetterPublishingRecoverer)",
        )
    }
}
