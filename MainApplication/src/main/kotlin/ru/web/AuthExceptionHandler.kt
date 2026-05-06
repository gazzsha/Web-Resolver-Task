package ru.web

import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import ru.taskresolver.model.exception.EmailAlreadyTakenException

@RestControllerAdvice
class AuthExceptionHandler {

    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentials(e: BadCredentialsException): ResponseEntity<Map<String, Any>> =
        ResponseEntity.status(401).body(errorBody("Invalid credentials"))

    @ExceptionHandler(EmailAlreadyTakenException::class)
    fun handleEmailTaken(e: EmailAlreadyTakenException): ResponseEntity<Map<String, Any>> =
        ResponseEntity.status(409).body(errorBody(e.message ?: "Email already taken"))

    private fun errorBody(msg: String): Map<String, Any> =
        mapOf("errorMessage" to msg, "errorDetails" to emptyMap<String, Any>())
}
