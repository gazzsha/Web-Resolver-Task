package ru.taskresolver.model.exception

data class NotCorrectArgumentException(
    override val message: String,
    val throwable: Throwable? = null
) : RuntimeException(message, throwable)
