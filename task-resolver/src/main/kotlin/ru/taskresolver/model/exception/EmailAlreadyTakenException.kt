package ru.taskresolver.model.exception

data class EmailAlreadyTakenException(
    override val message: String,
    val throwable: Throwable? = null,
) : RuntimeException(message, throwable)
