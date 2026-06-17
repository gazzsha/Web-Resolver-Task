package ru.security

import ru.db.entity.UserRole
import java.util.UUID

data class UserPrincipal(
    val id: UUID,
    val email: String,
    val role: UserRole,
)
