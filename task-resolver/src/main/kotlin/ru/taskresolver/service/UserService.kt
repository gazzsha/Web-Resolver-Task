package ru.taskresolver.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.db.entity.UserEntity
import ru.db.entity.UserRole
import ru.db.repository.UserRepository
import ru.taskresolver.model.exception.EmailAlreadyTakenException
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    @Transactional
    fun register(
        email: String,
        rawPassword: String,
        role: UserRole = UserRole.STUDENT,
    ): UserEntity {
        if (userRepository.existsByEmail(email)) {
            logger.warn { "Registration attempt with already taken email: $email" }
            throw EmailAlreadyTakenException("Email already taken: $email")
        }
        val entity = UserEntity(
            email = email,
            passwordHash = passwordEncoder.encode(rawPassword),
            role = role,
        )
        return userRepository.save(entity).also {
            logger.info { "Registered new user: id=${it.id}, role=${it.role}" }
        }
    }

    @Transactional(readOnly = true)
    fun authenticate(email: String, rawPassword: String): UserEntity {
        val user = userRepository.findByEmail(email)
        if (user == null || !passwordEncoder.matches(rawPassword, user.passwordHash)) {
            logger.warn { "Failed authentication attempt for email: $email" }
            throw BadCredentialsException("Invalid credentials")
        }
        return user
    }

    @Transactional(readOnly = true)
    fun findByEmail(email: String): UserEntity? = userRepository.findByEmail(email)

    @Transactional(readOnly = true)
    fun findById(id: UUID): UserEntity? = userRepository.findById(id).orElse(null)
}
