package ru.common.utils

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.kafka.core.KafkaTemplate

private val logger = KotlinLogging.logger { }

fun <V : Any> KafkaTemplate<String, V>.sendSyncAndLog(topic: String, data: V) {
    this.send(topic, data).get()
    // F-27: truncate payload — utility is shared, callers may pass large or
    // sensitive objects (e.g. WorkerTaskMessage with full student code).
    // 256 chars is enough to recognise the event shape in log forwarding.
    logger.info { "Published event to topic = $topic with data = ${data.toString().take(256)}" }
}
