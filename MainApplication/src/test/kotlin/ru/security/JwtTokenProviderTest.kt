package ru.security

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.db.entity.UserRole
import java.util.Date
import java.util.UUID

/**
 * Unit tests for JwtTokenProvider.
 *
 * Happy-path round-trips PLUS regression tests for the differential-review
 * findings F-15 (dev-secret fallback removed), F-22 (subject must be a UUID),
 * F-23 (issuer pinned, clock skew tolerated), and the standard negative-path
 * suite (tampered signature, expired token, wrong typ).
 */
class JwtTokenProviderTest {

    private companion object {
        private const val TEST_SECRET = "test-jwt-secret-32-bytes-padded!!"
        private const val TEST_ISSUER = "test-issuer"
    }

    private val provider = JwtTokenProvider(
        secret = TEST_SECRET,
        accessTtlMinutes = 60L,
        refreshTtlDays = 7L,
        issuer = TEST_ISSUER,
    )

    // ───────────────────────── Happy-path round-trips ─────────────────────────

    @Test
    fun `generateAccess includes username claim that survives round-trip`() {
        val userId = UUID.randomUUID()
        val email = "roundtrip@diplom.local"
        val role = UserRole.STUDENT
        val username = "roundtrip_user"

        val token = provider.generateAccess(userId, email, role, username)
        val claims = provider.parseAndValidate(token)

        assertEquals(userId.toString(), claims.subject)
        assertEquals("access", claims.typ)
        assertEquals(email, claims.email)
        assertEquals(role, claims.role)
        assertEquals(username, claims.username)
    }

    @Test
    fun `generateRefresh does not include username claim`() {
        val userId = UUID.randomUUID()

        val token = provider.generateRefresh(userId)
        val claims = provider.parseAndValidate(token)

        assertEquals(userId.toString(), claims.subject)
        assertEquals("refresh", claims.typ)
        assertEquals(null, claims.username)
        assertEquals(null, claims.email)
    }

    @Test
    fun `generateAccess with different usernames produces distinct tokens`() {
        val userId = UUID.randomUUID()
        val email = "distinct@diplom.local"
        val role = UserRole.TEACHER

        val token1 = provider.generateAccess(userId, email, role, "alice_1")
        val token2 = provider.generateAccess(userId, email, role, "bob_2")

        assertTrue(token1 != token2) { "Tokens with different usernames must be distinct" }

        val claims1 = provider.parseAndValidate(token1)
        val claims2 = provider.parseAndValidate(token2)

        assertEquals("alice_1", claims1.username)
        assertEquals("bob_2", claims2.username)
    }

    @Test
    fun `parseAndValidate extracts all standard claims correctly`() {
        val userId = UUID.randomUUID()
        val token = provider.generateAccess(userId, "full@diplom.local", UserRole.TEACHER, "full_user")
        val claims = provider.parseAndValidate(token)

        assertNotNull(claims.subject)
        assertNotNull(claims.email)
        assertNotNull(claims.role)
        assertNotNull(claims.username)
        assertEquals(UserRole.TEACHER, claims.role)
        assertEquals("full_user", claims.username)
    }

    // ───────────────────────── F-15 regression ────────────────────────────────

    /**
     * F-15: constructor MUST throw when JWT_SECRET is missing or shorter than
     * 32 bytes. Before the fix the constructor silently fell back to a baked-in
     * dev secret, letting forge-able tokens validate on every deploy that
     * forgot to set the env var.
     */
    @Test
    fun `constructor rejects empty secret`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            JwtTokenProvider(secret = "", accessTtlMinutes = 60L, refreshTtlDays = 7L, issuer = TEST_ISSUER)
        }
        assertTrue(ex.message!!.contains("JWT_SECRET")) { "Expected message about JWT_SECRET, got: ${ex.message}" }
    }

    @Test
    fun `constructor rejects secret shorter than 32 bytes`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            JwtTokenProvider(secret = "too-short", accessTtlMinutes = 60L, refreshTtlDays = 7L, issuer = TEST_ISSUER)
        }
        assertTrue(ex.message!!.contains("32 bytes"))
    }

    // ───────────────────────── F-22 regression ────────────────────────────────

    /**
     * F-22: subject must be a UUID. Tokens forged with arbitrary subject
     * strings (e.g. SQL-injection payloads) must be rejected at parse time
     * with a clear JwtException, not at downstream UUID.fromString().
     */
    @Test
    fun `parseAndValidate rejects token with non-UUID subject`() {
        val key = Keys.hmacShaKeyFor(TEST_SECRET.toByteArray())
        val tokenWithBadSubject = Jwts.builder()
            .subject("admin'; DROP TABLE users;--")
            .issuer(TEST_ISSUER)
            .claim("typ", "access")
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(key)
            .compact()

        val ex = assertThrows(JwtException::class.java) {
            provider.parseAndValidate(tokenWithBadSubject)
        }
        assertTrue(ex.message!!.contains("UUID")) { "Expected message about UUID, got: ${ex.message}" }
    }

    // ───────────────────────── F-23 regression ────────────────────────────────

    /**
     * F-23: issuer mismatch must reject. Pinning prevents tokens issued by
     * staging (or any other deployment that accidentally shares the HMAC key)
     * from validating against production.
     */
    @Test
    fun `parseAndValidate rejects token with wrong issuer`() {
        val foreignProvider = JwtTokenProvider(
            secret = TEST_SECRET,
            accessTtlMinutes = 60L,
            refreshTtlDays = 7L,
            issuer = "some-other-deployment",
        )
        val foreignToken = foreignProvider.generateAccess(
            UUID.randomUUID(), "x@y.z", UserRole.STUDENT, "u",
        )

        assertThrows(JwtException::class.java) {
            provider.parseAndValidate(foreignToken)
        }
    }

    /**
     * F-23: clockSkewSeconds(30) — a token whose `exp` is 10 s in the past
     * MUST still validate (network jitter / NTP drift tolerance).
     */
    @Test
    fun `parseAndValidate tolerates 10 second expiry skew`() {
        val key = Keys.hmacShaKeyFor(TEST_SECRET.toByteArray())
        val tenSecondsAgo = Date(System.currentTimeMillis() - 10_000)
        val barelyExpiredToken = Jwts.builder()
            .subject(UUID.randomUUID().toString())
            .issuer(TEST_ISSUER)
            .claim("typ", "access")
            .issuedAt(Date(System.currentTimeMillis() - 70_000))
            .expiration(tenSecondsAgo)
            .signWith(key)
            .compact()

        // No exception expected — within the 30 s skew window.
        val claims = provider.parseAndValidate(barelyExpiredToken)
        assertEquals("access", claims.typ)
    }

    // ───────────────────────── Negative-path suite ────────────────────────────

    @Test
    fun `parseAndValidate rejects tampered signature`() {
        val token = provider.generateAccess(
            UUID.randomUUID(), "x@y.z", UserRole.STUDENT, "u",
        )
        // JWT format: header.payload.signature. Replace the signature segment
        // entirely with a valid-looking-but-wrong base64url string of the same
        // length, so the JWT parses structurally but fails HMAC verification.
        val parts = token.split(".")
        check(parts.size == 3) { "Expected 3 JWT segments, got ${parts.size}" }
        val tamperedSig = parts[2].map { c -> if (c.isLetterOrDigit()) 'X' else c }.joinToString("")
        val tampered = "${parts[0]}.${parts[1]}.$tamperedSig"

        assertThrows(JwtException::class.java) {
            provider.parseAndValidate(tampered)
        }
    }

    @Test
    fun `parseAndValidate rejects long-expired token`() {
        val key = Keys.hmacShaKeyFor(TEST_SECRET.toByteArray())
        val oneHourAgo = Date(System.currentTimeMillis() - 60 * 60 * 1000)
        val expiredToken = Jwts.builder()
            .subject(UUID.randomUUID().toString())
            .issuer(TEST_ISSUER)
            .claim("typ", "access")
            .issuedAt(Date(System.currentTimeMillis() - 2 * 60 * 60 * 1000))
            .expiration(oneHourAgo)
            .signWith(key)
            .compact()

        assertThrows(JwtException::class.java) {
            provider.parseAndValidate(expiredToken)
        }
    }

    @Test
    fun `parseAndValidate accepts both access and refresh typ but caller must distinguish`() {
        val userId = UUID.randomUUID()
        val access = provider.generateAccess(userId, "x@y.z", UserRole.STUDENT, "u")
        val refresh = provider.generateRefresh(userId)

        assertEquals("access", provider.parseAndValidate(access).typ)
        assertEquals("refresh", provider.parseAndValidate(refresh).typ)
        // Note: the typ-mismatch enforcement lives at the AuthController layer
        // (refresh endpoint requires typ=refresh, JwtAuthenticationFilter requires
        // typ=access). The provider itself only ensures the claim is present.
    }

    @Test
    fun `parseAndValidate rejects token signed with different secret`() {
        val foreignKey = Keys.hmacShaKeyFor("an-entirely-different-32b-secret".toByteArray())
        val foreignToken = Jwts.builder()
            .subject(UUID.randomUUID().toString())
            .issuer(TEST_ISSUER)
            .claim("typ", "access")
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(foreignKey)
            .compact()

        assertThrows(JwtException::class.java) {
            provider.parseAndValidate(foreignToken)
        }
    }
}
