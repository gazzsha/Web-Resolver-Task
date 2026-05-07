package ru.db.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
data class UserEntity(
    @Id
    @Column(name = "id", nullable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "email", nullable = false, unique = true, length = 255)
    val email: String,

    @Column(name = "password_hash", nullable = false, length = 255)
    val passwordHash: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    val role: UserRole,

    @field:Size(min = 3, max = 32, message = "Имя пользователя должно содержать от 3 до 32 символов")
    @field:Pattern(
        regexp = "^[a-zA-Z0-9_]+$",
        message = "Имя пользователя может содержать только латинские буквы, цифры и символ подчёркивания",
    )
    @Column(name = "username", nullable = false, length = 32)
    val username: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
