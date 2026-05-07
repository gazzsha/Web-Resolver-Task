package ru.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import ru.db.entity.UserRole
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

private val logger = LoggerFactory.getLogger(JwtTokenProvider::class.java)

@Component
class JwtTokenProvider(
    @Value("\${jwt.secret}") secret: String,
    @Value("\${jwt.access-ttl-minutes}") private val accessTtlMinutes: Long,
    @Value("\${jwt.refresh-ttl-days}") private val refreshTtlDays: Long,
) {
    private val key: SecretKey = if (secret.length >= 32) {
        Keys.hmacShaKeyFor(secret.toByteArray(Charsets.UTF_8))
    } else {
        // WARNING: JWT_SECRET is not set or too short — using insecure dev key. Set JWT_SECRET in production.
        logger.warn("JWT_SECRET is empty or shorter than 32 bytes — using insecure dev secret. DO NOT use in production.")
        val devSecret = "dev-secret-32-bytes-padded-here!!"
        Keys.hmacShaKeyFor(devSecret.toByteArray(Charsets.UTF_8))
    }

    fun generateAccess(userId: UUID, email: String, role: UserRole, username: String): String {
        val now = System.currentTimeMillis()
        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("role", role.name)
            .claim("username", username)
            .claim("typ", "access")
            .issuedAt(Date(now))
            .expiration(Date(now + accessTtlMinutes * 60 * 1000))
            .signWith(key)
            .compact()
    }

    fun generateRefresh(userId: UUID): String {
        val now = System.currentTimeMillis()
        return Jwts.builder()
            .subject(userId.toString())
            .claim("typ", "refresh")
            .issuedAt(Date(now))
            .expiration(Date(now + refreshTtlDays * 24 * 60 * 60 * 1000))
            .signWith(key)
            .compact()
    }

    fun parseAndValidate(token: String): JwtClaims {
        val claims: Claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload

        return JwtClaims(
            subject = claims.subject,
            typ = claims.get("typ", String::class.java) ?: throw JwtException("Missing typ claim"),
            email = claims.get("email", String::class.java),
            role = claims.get("role", String::class.java)?.let { runCatching { UserRole.valueOf(it) }.getOrNull() },
            username = claims.get("username", String::class.java),
        )
    }
}

data class JwtClaims(
    val subject: String,
    val typ: String,
    val email: String?,
    val role: UserRole?,
    val username: String? = null,
)
