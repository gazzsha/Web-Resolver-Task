package ru.taskresolver.model.exception

data class NotFoundException(
    override val message: String,
    val throwable: Throwable? = null
) : RuntimeException(message, throwable)
