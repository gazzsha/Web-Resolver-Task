package ru.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import ru.db.entity.UserRole
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    @Value("\${jwt.secret}") secret: String,
    @Value("\${jwt.access-ttl-minutes}") private val accessTtlMinutes: Long,
    @Value("\${jwt.refresh-ttl-days}") private val refreshTtlDays: Long,
    @Value("\${jwt.issuer:web-resolver-task}") private val issuer: String,
) {
    // F-15: fail-fast. Source-baked dev fallback removed — boot fails when
    // JWT_SECRET is unset or shorter than 32 bytes (HS256 minimum). Previously
    // the app would silently fall back to a publicly-known constant, allowing
    // a TEACHER token to be forged from a copy of this repository.
    private val key: SecretKey = run {
        require(secret.length >= 32) {
            "JWT_SECRET must be set and at least 32 bytes (got length=${secret.length}). " +
                "Generate one with: openssl rand -hex 32"
        }
        Keys.hmacShaKeyFor(secret.toByteArray(Charsets.UTF_8))
    }

    fun generateAccess(userId: UUID, email: String, role: UserRole, username: String): String {
        val now = System.currentTimeMillis()
        return Jwts.builder()
            .subject(userId.toString())
            .issuer(issuer)
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
            .issuer(issuer)
            .claim("typ", "refresh")
            .issuedAt(Date(now))
            .expiration(Date(now + refreshTtlDays * 24 * 60 * 60 * 1000))
            .signWith(key)
            .compact()
    }

    fun parseAndValidate(token: String): JwtClaims {
        // F-23: clockSkewSeconds(30) tolerates ±30 s wall-clock drift between
        // pods. requireIssuer pins tokens to this deployment — a token from
        // staging will not validate in prod even if the HMAC key was reused.
        val claims: Claims = Jwts.parser()
            .verifyWith(key)
            .clockSkewSeconds(30)
            .requireIssuer(issuer)
            .build()
            .parseSignedClaims(token)
            .payload

        // F-22: subject must be a UUID — otherwise downstream UUID.fromString
        // throws IllegalArgumentException inside the JwtAuthenticationFilter
        // and the failure is swallowed at DEBUG, making forged-but-malformed
        // tokens invisible to operators.
        val subject = claims.subject ?: throw JwtException("Missing subject claim")
        runCatching { UUID.fromString(subject) }.getOrElse {
            throw JwtException("Subject is not a UUID: ${subject.take(40)}")
        }

        return JwtClaims(
            subject = subject,
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
