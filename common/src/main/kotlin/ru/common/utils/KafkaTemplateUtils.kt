package ru.common.utils

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.kafka.core.KafkaTemplate

private val logger = KotlinLogging.logger { }

fun <V : Any> KafkaTemplate<String, V>.sendSyncAndLog(topic: String, data: V) {
    this.send(topic, data).get()
    logger.info { "Published event to topic = $topic with data = $data" }
}
