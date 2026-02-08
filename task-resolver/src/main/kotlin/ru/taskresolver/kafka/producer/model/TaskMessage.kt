package ru.taskresolver.kafka.producer.model

import java.util.UUID

data class TaskMessage(
    val taskId: UUID,
    val code: String,
    val language: String,
    val testId: UUID
)
